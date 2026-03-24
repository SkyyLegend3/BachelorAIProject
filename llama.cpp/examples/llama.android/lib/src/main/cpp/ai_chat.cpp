#include <android/log.h>
#include <jni.h>
#include <chrono>
#include <atomic>
#include <iomanip>
#include <cmath>
#include <string>
#include <unistd.h>
#include <sampling.h>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

/**
 * LLama resources: context, model, batch and sampler
 */
constexpr int   N_THREADS_MIN           = 2;
constexpr int   N_THREADS_MAX           = 4;
constexpr int   N_THREADS_HEADROOM      = 2;

constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;
constexpr int   OVERFLOW_HEADROOM       = 4;
constexpr int   BATCH_SIZE              = 512;
constexpr int   DIRECT_MOBILE_BATCH_MAX = 32;
constexpr int   DIRECT_PREFILL_CHUNK    = 1;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;

static int   g_runtime_n_ctx        = DEFAULT_CONTEXT_SIZE;
static float g_runtime_sampler_temp = DEFAULT_SAMPLER_TEMP;
static int   g_runtime_threads_min  = N_THREADS_MIN;
static int   g_runtime_threads_max  = N_THREADS_MAX;

static llama_model                      * g_model;
static llama_context                    * g_context;
static llama_batch                        g_batch;
static common_chat_templates_ptr          g_chat_templates;
static common_sampler                   * g_sampler;
static std::atomic_bool                   g_direct_cancel_requested{false};
static bool                               g_batch_initialized = false;
static int                                g_direct_batch_capacity = 0;

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_init(JNIEnv *env, jobject /*unused*/, jstring nativeLibDir) {
    // Set llama log handler to Android
    llama_log_set(aichat_android_log_callback, nullptr);

    // Loading all CPU backend variants
    const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
    LOGi("Loading backends from %s", path_to_backend);
    ggml_backend_load_all_from_path(path_to_backend);
    env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

    // Initialize backends
    llama_backend_init();
    LOGi("Backend initiated; Log handler set.");
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    llama_model_params model_params = llama_model_default_params();

    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
    LOGd("%s: Loading model from: \n%s\n", __func__, model_path);

    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!model) {
        return 1;
    }
    g_model = model;
    return 0;
}

static llama_context *init_context(llama_model *model, const int n_ctx = DEFAULT_CONTEXT_SIZE) {
    if (!model) {
        LOGe("%s: model cannot be null", __func__);
        return nullptr;
    }

    // Multi-threading setup
    const int n_threads_min = std::max(1, g_runtime_threads_min);
    const int n_threads_max = std::max(n_threads_min, g_runtime_threads_max);
    const int n_threads = std::max(n_threads_min, std::min(n_threads_max,
                                                     (int) sysconf(_SC_NPROCESSORS_ONLN) -
                                                     N_THREADS_HEADROOM));
    LOGi("%s: Using %d threads", __func__, n_threads);

    // Context parameters setup
    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (n_ctx > trained_context_size) {
        LOGw("%s: Model was trained with only %d context size! Enforcing %d context size...",
             __func__, trained_context_size, n_ctx);
    }
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    auto *context = llama_init_from_model(g_model, ctx_params);
    if (context == nullptr) {
        LOGe("%s: llama_new_context_with_model() returned null)", __func__);
    }
    return context;
}

static common_sampler *new_sampler(float temp) {
    common_params_sampling sparams;
    sparams.temp = temp;
    return common_sampler_init(g_model, sparams);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_configureGeneration(
        JNIEnv * /*env*/,
        jobject /*unused*/,
        jint n_ctx,
        jfloat temperature,
        jint threads_min,
        jint threads_max
) {
    g_runtime_n_ctx = std::max(128, (int) n_ctx);
    g_runtime_sampler_temp = std::max(0.0f, (float) temperature);
    g_runtime_threads_min = std::max(1, (int) threads_min);
    g_runtime_threads_max = std::max(g_runtime_threads_min, (int) threads_max);

    LOGi(
        "%s: n_ctx=%d, temperature=%.3f, threads=%d-%d",
        __func__, g_runtime_n_ctx, g_runtime_sampler_temp, g_runtime_threads_min, g_runtime_threads_max
    );
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(JNIEnv * /*env*/, jobject /*unused*/) {
    auto *context = init_context(g_model, g_runtime_n_ctx);
    if (!context) { return 1; }
    g_context = context;
    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");
    g_sampler = new_sampler(g_runtime_sampler_temp);
    return 0;
}

static std::string get_backend() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    return backends.empty() ? "CPU" : join(backends, ",");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject /*unused*/) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_benchModel(JNIEnv *env, jobject /*unused*/, jint pp, jint tg,
                                                      jint pl, jint nr) {
    auto *context = init_context(g_model, pp);
    if (!context) {
        const auto *const err_msg = "Fail to init_context! Bench aborted.";
        LOGe(err_msg);
        return env->NewStringUTF(err_msg);
    }

    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    const uint32_t n_ctx = llama_n_ctx(context);
    LOGi("n_ctx = %d", n_ctx);

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        LOGi("Benchmark prompt processing (pp = %d)", pp);

        common_batch_clear(g_batch);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            common_batch_add(g_batch, 0, i, {0}, false);
        }

        g_batch.logits[g_batch.n_tokens - 1] = true;
        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp_start = ggml_time_us();
        if (llama_decode(context, g_batch) != 0) {
            LOGe("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        // bench text generation

        LOGi("Benchmark text generation (tg = %d)", tg);

        llama_memory_clear(llama_get_memory(context), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {
            common_batch_clear(g_batch);
            for (j = 0; j < pl; j++) {
                common_batch_add(g_batch, 0, i, {j}, true);
            }

            if (llama_decode(context, g_batch) != 0) {
                LOGe("llama_decode() failed during text generation");
            }
        }
        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;

        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;

        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;

        LOGi("pp %f t/s, tg %f t/s", speed_pp, speed_tg);
    }

    llama_free(context);

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(g_model, model_desc, sizeof(model_desc));

    const auto model_size = double(llama_model_size(g_model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(g_model)) / 1e9;

    const auto backend = get_backend();
    std::stringstream result;
    result << std::setprecision(3);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";
    return env->NewStringUTF(result.str().c_str());
}


/**
 * Completion loop's long-term states:
 * - chat management
 * - position tracking
 */
constexpr const char *ROLE_SYSTEM       = "system";
constexpr const char *ROLE_USER         = "user";
constexpr const char *ROLE_ASSISTANT    = "assistant";

static std::vector<common_chat_msg> chat_msgs;
static llama_pos system_prompt_position;
static llama_pos current_position;

static void reset_long_term_states(const bool clear_kv_cache = true) {
    chat_msgs.clear();
    system_prompt_position = 0;
    current_position = 0;

    if (clear_kv_cache)
        llama_memory_clear(llama_get_memory(g_context), false);
}

/**
 * TODO-hyin: implement sliding-window version as a better alternative
 *
 * Context shifting by discarding the older half of the tokens appended after system prompt:
 * - take the [system_prompt_position] first tokens from the original prompt
 * - take half of the last (system_prompt_position - system_prompt_position) tokens
 * - recompute the logits in batches
 */
static void shift_context() {
    const int n_discard = (current_position - system_prompt_position) / 2;
    LOGi("%s: Discarding %d tokens", __func__, n_discard);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, system_prompt_position + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, system_prompt_position + n_discard, current_position, -n_discard);
    current_position -= n_discard;
    LOGi("%s: Context shifting done! Current position: %d", __func__, current_position);
}

static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), chat_msgs, new_msg, role == ROLE_USER, /* use_jinja */ false);
    chat_msgs.push_back(new_msg);
    LOGi("%s: Formatted and added %s message: \n%s\n", __func__, role.c_str(), formatted.c_str());
    return formatted;
}

/**
 * Completion loop's short-term states:
 * - stop generation position
 * - token chars caching
 * - current assistant message being generated
 */
static llama_pos stop_generation_position;
static std::string cached_token_chars;
static std::ostringstream assistant_ss;

static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars.clear();
    assistant_ss.str("");
}

static bool try_extract_complete_json_object(
        const std::string &text,
        std::string &json_out
) {
    int start = -1;
    int depth = 0;
    bool in_string = false;
    bool escaped = false;

    for (int i = 0; i < (int) text.size(); ++i) {
        const char ch = text[i];

        if (escaped) {
            escaped = false;
            continue;
        }

        if (ch == '\\' && in_string) {
            escaped = true;
            continue;
        }

        if (ch == '"') {
            in_string = !in_string;
            continue;
        }

        if (in_string) {
            continue;
        }

        if (ch == '{') {
            if (depth == 0) {
                start = i;
            }
            depth++;
            continue;
        }

        if (ch == '}') {
            if (depth == 0) {
                continue;
            }

            depth--;
            if (depth == 0 && start >= 0) {
                json_out = text.substr(start, i - start + 1);
                return true;
            }
        }
    }

    return false;
}

static int decode_tokens_in_batches(
        llama_context *context,
        llama_batch &batch,
        const llama_tokens &tokens,
        const llama_pos start_pos,
        const bool compute_last_logit = false) {
    // Process tokens in batches using the global batch
    LOGd("%s: Decode %d tokens starting at position %d", __func__, (int) tokens.size(), start_pos);
    for (int i = 0; i < (int) tokens.size(); i += BATCH_SIZE) {
        const int cur_batch_size = std::min((int) tokens.size() - i, BATCH_SIZE);
        common_batch_clear(batch);
        LOGv("%s: Preparing a batch size of %d starting at: %d", __func__, cur_batch_size, i);

        // Shift context if current batch cannot fit into the context
        if (start_pos + i + cur_batch_size >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            LOGw("%s: Current batch won't fit into context! Shifting...", __func__);
            shift_context();
        }

        // Add tokens to the batch with proper positions
        for (int j = 0; j < cur_batch_size; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position = start_pos + i + j;
            const bool want_logit = compute_last_logit && (i + j == tokens.size() - 1);
            common_batch_add(batch, token_id, position, {0}, want_logit);
        }

        // Decode this batch
        const int decode_result = llama_decode(context, batch);
        if (decode_result) {
            LOGe("%s: llama_decode failed w/ %d", __func__, decode_result);
            return 1;
        }
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processSystemPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jsystem_prompt
) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Obtain system prompt from JEnv
    const auto *system_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    LOGd("%s: System prompt received: \n%s", __func__, system_prompt);
    std::string formatted_system_prompt(system_prompt);
    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);

    // Format system prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_system_prompt = chat_add_and_format(ROLE_SYSTEM, system_prompt);
    }

    // Tokenize system prompt
    const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,
                                               has_chat_template, has_chat_template);
    for (auto id: system_tokens) {
        LOGv("token: `%s`\t -> `%d`", common_token_to_piece(g_context, id).c_str(), id);
    }

    // Handle context overflow
    const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if ((int) system_tokens.size() > max_batch_size) {
        LOGe("%s: System prompt too long for context! %d tokens, max: %d",
             __func__, (int) system_tokens.size(), max_batch_size);
        return 1;
    }

    // Decode system tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, system_tokens, current_position)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    system_prompt_position = current_position = (int) system_tokens.size();
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict
) {
    // Reset short-term states
    reset_short_term_states();

    // Obtain and tokenize user prompt
    const auto *const user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    LOGd("%s: User prompt received: \n%s", __func__, user_prompt);
    std::string formatted_user_prompt(user_prompt);
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    // Format user prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_user_prompt = chat_add_and_format(ROLE_USER, user_prompt);
    }

    // Decode formatted user prompts
    auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template);
    for (auto id: user_tokens) {
        LOGv("token: `%s`\t -> `%d`", common_token_to_piece(g_context, id).c_str(), id);
    }

    // Ensure user prompt doesn't exceed the context size by truncating if necessary.
    const int user_prompt_size = (int) user_tokens.size();
    const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if (user_prompt_size > max_batch_size) {
        const int skipped_tokens = user_prompt_size - max_batch_size;
        user_tokens.resize(max_batch_size);
        LOGw("%s: User prompt too long! Skipped %d tokens!", __func__, skipped_tokens);
    }

    // Decode user tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, user_tokens, current_position, true)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    current_position += user_prompt_size;
    stop_generation_position = current_position + user_prompt_size + n_predict;
    return 0;
}

static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_generateNextToken(
        JNIEnv *env,
        jobject /*unused*/
) {
    // Infinite text generation via context shifting
    if (current_position >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
        LOGw("%s: Context full! Shifting...", __func__);
        shift_context();
    }

    // Stop if reaching the marked position
    if (current_position >= stop_generation_position) {
        LOGw("%s: STOP: hitting stop position: %d", __func__, stop_generation_position);
        return nullptr;
    }

    // Sample next token
    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    // Populate the batch with new token, then decode
    common_batch_clear(g_batch);
    common_batch_add(g_batch, new_token_id, current_position, {0}, true);
    if (llama_decode(g_context, g_batch) != 0) {
        LOGe("%s: llama_decode() failed for generated token", __func__);
        return nullptr;
    }

    // Update position
    current_position++;

    // Stop if next token is EOG
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        LOGd("id: %d,\tIS EOG!\nSTOP.", new_token_id);
        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        return nullptr;
    }

    // If not EOG, convert to text
    auto new_token_chars = common_token_to_piece(g_context, new_token_id);
    cached_token_chars += new_token_chars;

    // Create and return a valid UTF-8 Java string
    jstring result = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        result = env->NewStringUTF(cached_token_chars.c_str());
        LOGv("id: %d,\tcached: `%s`,\tnew: `%s`", new_token_id, cached_token_chars.c_str(), new_token_chars.c_str());

        assistant_ss << cached_token_chars;
        cached_token_chars.clear();
    } else {
        LOGv("id: %d,\tappend to cache", new_token_id);
        result = env->NewStringUTF("");
    }
    return result;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_unload(JNIEnv * /*unused*/, jobject /*unused*/) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Free up resources
    common_sampler_free(g_sampler);
    g_chat_templates.reset();
    llama_batch_free(g_batch);
    llama_free(g_context);
    llama_model_free(g_model);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_shutdown(JNIEnv *, jobject /*unused*/) {
    llama_backend_free();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_direct_DirectLlamaBridge_nativeInit(
        JNIEnv *env,
        jobject /*unused*/,
        jstring nativeLibDir
) {
    Java_com_arm_aichat_internal_InferenceEngineImpl_init(env, nullptr, nativeLibDir);
    return 0;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_direct_DirectLlamaBridge_nativeLoadModel(
        JNIEnv *env,
        jobject /*unused*/,
        jstring modelPath,
        jint n_ctx,
        jfloat temperature,
        jint threads_min,
        jint threads_max
) {
    g_runtime_n_ctx = std::max(128, (int) n_ctx);
    g_runtime_sampler_temp = std::max(0.0f, (float) temperature);
    g_runtime_threads_min = std::max(1, (int) threads_min);
    g_runtime_threads_max = std::max(g_runtime_threads_min, (int) threads_max);

    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_batch_initialized) {
        llama_batch_free(g_batch);
        g_batch_initialized = false;
    }
    g_direct_batch_capacity = 0;
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_chat_templates != nullptr) {
        g_chat_templates.reset();
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    const char *model_path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_model_load_from_file(model_path, model_params);

    env->ReleaseStringUTFChars(modelPath, model_path);

    if (g_model == nullptr) {
        return 1;
    }

    const int n_threads = std::max(
            g_runtime_threads_min,
            std::min(
                    g_runtime_threads_max,
                    (int) sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM
            )
    );

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = g_runtime_n_ctx;
    const int mobile_batch = std::max(16, std::min(DIRECT_MOBILE_BATCH_MAX, g_runtime_n_ctx));
    ctx_params.n_batch = mobile_batch;
    ctx_params.n_ubatch = mobile_batch;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    LOGi("DIRECT load: threads=%d, n_ctx=%d, n_batch=%u n_ubatch=%u",
            n_threads,
            g_runtime_n_ctx,
            ctx_params.n_batch,
            ctx_params.n_ubatch);

    g_context = llama_init_from_model(g_model, ctx_params);
    if (g_context == nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
        return 2;
    }

    g_chat_templates = common_chat_templates_init(g_model, "");
    if (g_chat_templates == nullptr) {
        llama_free(g_context);
        g_context = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        return 3;
    }

    common_params_sampling sparams;
    sparams.temp = g_runtime_sampler_temp;
    g_sampler = common_sampler_init(g_model, sparams);
    if (g_sampler == nullptr) {
        g_chat_templates.reset();
        llama_free(g_context);
        g_context = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        return 4;
    }

    g_batch = llama_batch_init(mobile_batch, 0, 1);
    g_batch_initialized = true;
    g_direct_batch_capacity = mobile_batch;

    g_direct_cancel_requested.store(false);
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_direct_DirectLlamaBridge_nativeCancel(
        JNIEnv * /*env*/,
        jobject /*unused*/
) {
    g_direct_cancel_requested.store(true);
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_direct_DirectLlamaBridge_nativeInfer(
        JNIEnv *env,
        jobject /*unused*/,
        jstring systemPrompt,
        jstring userPrompt,
        jint n_predict,
        jlong timeout_ms
) {
    if (g_model == nullptr || g_context == nullptr || g_sampler == nullptr) {
        LOGe("DIRECT infer: model not loaded");
        return env->NewStringUTF("__DIRECT_LLM_ERROR__: model not loaded");
    }

    g_direct_cancel_requested.store(false);

    const char *sys_chars = env->GetStringUTFChars(systemPrompt, nullptr);
    const char *usr_chars = env->GetStringUTFChars(userPrompt, nullptr);

    std::string sys = sys_chars ? sys_chars : "";
    std::string usr = usr_chars ? usr_chars : "";

    if (sys_chars != nullptr) {
        env->ReleaseStringUTFChars(systemPrompt, sys_chars);
    }
    if (usr_chars != nullptr) {
        env->ReleaseStringUTFChars(userPrompt, usr_chars);
    }

    LOGi("DIRECT infer: start (sys=%d, usr=%d, predict=%d, timeout_ms=%lld)",
         (int) sys.size(), (int) usr.size(), (int) n_predict, (long long) timeout_ms);

    reset_long_term_states();

    std::string prompt;
    if (!sys.empty()) {
        prompt += chat_add_and_format(ROLE_SYSTEM, sys);
    }
    if (!usr.empty()) {
        prompt += chat_add_and_format(ROLE_USER, usr);
    }
    if (prompt.empty()) {
        prompt = usr;
    }

    LOGi("DIRECT infer: prompt_len=%d", (int) prompt.size());

    const auto started = std::chrono::steady_clock::now();

    auto is_timed_out = [&]() -> bool {
        if (timeout_ms <= 0) return false;
        const auto elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - started
        ).count();
        return elapsed_ms >= timeout_ms;
    };

    auto elapsed_ms_now = [&]() -> long long {
        return (long long) std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - started
        ).count();
    };

    if (g_direct_cancel_requested.load()) {
        LOGw("DIRECT infer: cancelled before reset");
        return env->NewStringUTF("__DIRECT_LLM_ERROR__: cancelled");
    }
    if (is_timed_out()) {
        LOGw("DIRECT infer: timeout before reset");
        return env->NewStringUTF("__DIRECT_LLM_ERROR__: timeout");
    }

    llama_memory_clear(llama_get_memory(g_context), false);

    common_sampler_reset(g_sampler);

    if (g_direct_cancel_requested.load()) {
        LOGw("DIRECT infer: cancelled before tokenize");
        return env->NewStringUTF("__DIRECT_LLM_ERROR__: cancelled");
    }
    if (is_timed_out()) {
        LOGw("DIRECT infer: timeout before tokenize");
        return env->NewStringUTF("__DIRECT_LLM_ERROR__: timeout");
    }

    std::vector<llama_token> tokens_list = common_tokenize(g_context, prompt, true, true);
    LOGi("DIRECT infer: token_count=%d elapsed_ms=%lld", (int) tokens_list.size(), elapsed_ms_now());

    if (tokens_list.empty()) {
        LOGe("DIRECT infer: tokenization failed");
        return env->NewStringUTF("__DIRECT_LLM_ERROR__: tokenization failed");
    }

    if ((int) tokens_list.size() >= g_runtime_n_ctx) {
        LOGe(
                "DIRECT infer: prompt too large token_count=%d n_ctx=%d",
                (int) tokens_list.size(),
                g_runtime_n_ctx
        );
        return env->NewStringUTF("__DIRECT_LLM_ERROR__: prompt too large");
    }

    if (!g_batch_initialized) {
        LOGe("DIRECT infer: batch not initialized");
        return env->NewStringUTF("__DIRECT_LLM_ERROR__: batch not initialized");
    }

    // Prefill in mobilen Batches, damit Timeout/Cancel regelmaessig greifen koennen,
    // ohne fuer jedes einzelne Prompt-Token einen separaten Decode auszufuehren.
      const int prefill_chunk = std::max(
              1,
              std::min(
                      DIRECT_PREFILL_CHUNK,
                      std::min((int) tokens_list.size(), g_direct_batch_capacity)
              )
      );
    LOGi("DIRECT infer: prefill_chunk=%d batch_capacity=%d", prefill_chunk, g_direct_batch_capacity);
    for (int i = 0; i < (int) tokens_list.size(); i += prefill_chunk) {
        if (g_direct_cancel_requested.load()) {
            LOGw("DIRECT infer: cancelled during prefill at token=%d", i);
            return env->NewStringUTF("__DIRECT_LLM_ERROR__: cancelled");
        }
        if (is_timed_out()) {
            LOGw("DIRECT infer: timeout during prefill before decode token=%d elapsed_ms=%lld", i, elapsed_ms_now());
            return env->NewStringUTF("__DIRECT_LLM_ERROR__: timeout");
        }

        const int cur = std::min(prefill_chunk, (int) tokens_list.size() - i);
        common_batch_clear(g_batch);
        for (int j = 0; j < cur; ++j) {
            const bool want_logits = (i + j == (int) tokens_list.size() - 1);
            common_batch_add(g_batch, tokens_list[i + j], i + j, {0}, want_logits);
        }

        const int decode_result = llama_decode(g_context, g_batch);
        if (decode_result != 0) {
            LOGe("DIRECT infer: prefill decode failed token=%d decode_result=%d", i, decode_result);
            return env->NewStringUTF("__DIRECT_LLM_ERROR__: prefill decode failed");
        }

        if (is_timed_out()) {
            LOGw("DIRECT infer: timeout during prefill after decode token=%d elapsed_ms=%lld", i + cur, elapsed_ms_now());
            return env->NewStringUTF("__DIRECT_LLM_ERROR__: timeout");
        }
    }
    current_position = (llama_pos) tokens_list.size();
    LOGi("DIRECT infer: prefill done elapsed_ms=%lld", elapsed_ms_now());

    std::string output;
    output.reserve(256);

    LOGi("DIRECT infer: generation start");
    for (int i = 0; i < n_predict; ++i) {
        if (g_direct_cancel_requested.load()) {
            LOGw("DIRECT infer: cancelled during generation step=%d", i);
            return env->NewStringUTF("__DIRECT_LLM_ERROR__: cancelled");
        }
        if (is_timed_out()) {
            LOGw(
                    "DIRECT infer: timeout during generation step=%d elapsed_ms=%lld",
                    i,
                    elapsed_ms_now()
            );
            return env->NewStringUTF("__DIRECT_LLM_ERROR__: timeout");
        }

        const llama_token new_token_id = common_sampler_sample(g_sampler, g_context, -1);
        common_sampler_accept(g_sampler, new_token_id, true);

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
            LOGi(
                    "DIRECT infer: reached EOG at step=%d elapsed_ms=%lld",
                    i,
                    elapsed_ms_now()
            );
            break;
        }

        auto piece = common_token_to_piece(g_context, new_token_id);
        output += piece;

        std::string completed_json;
        if (try_extract_complete_json_object(output, completed_json)) {
            output = completed_json;
            LOGi(
                    "DIRECT infer: completed json at step=%d elapsed_ms=%lld output=%s",
                    i,
                    elapsed_ms_now(),
                    output.c_str()
            );
            break;
        }

        common_batch_clear(g_batch);
        common_batch_add(g_batch, new_token_id, current_position, {0}, true);
        const int decode_result = llama_decode(g_context, g_batch);
        current_position++;

        if (decode_result != 0) {
            LOGe("DIRECT infer: token decode failed step=%d decode_result=%d elapsed_ms=%lld",
                 i, decode_result, elapsed_ms_now());
            return env->NewStringUTF("__DIRECT_LLM_ERROR__: token decode failed");
        }
    }

    LOGi(
            "DIRECT infer: done elapsed_ms=%lld output_len=%d output_preview=%s",
            elapsed_ms_now(),
            (int) output.size(),
            output.substr(0, std::min((size_t) 120, output.size())).c_str()
    );

    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_direct_DirectLlamaBridge_nativeUnload(
        JNIEnv * /*env*/,
        jobject /*unused*/
) {
    g_direct_cancel_requested.store(false);

    if (g_batch_initialized) {
        llama_batch_free(g_batch);
        g_batch_initialized = false;
    }
    g_direct_batch_capacity = 0;
    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_chat_templates != nullptr) {
        g_chat_templates.reset();
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_direct_DirectLlamaBridge_nativeShutdown(
        JNIEnv *env,
        jobject /*unused*/
) {
    Java_com_arm_aichat_internal_InferenceEngineImpl_shutdown(env, nullptr);
}
