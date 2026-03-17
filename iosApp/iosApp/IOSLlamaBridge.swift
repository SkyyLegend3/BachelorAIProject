import Foundation
import ComposeApp

@objc(IOSLlamaBridge)
final class IOSLlamaBridge: NSObject, IosLlmBridge {
    private var context: AnyObject?
    private var currentModelPath: String = ""
    private let lock = NSLock()

    func completeJson(
        modelPath: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int32
    ) -> String {
#if canImport(llama)
        lock.lock()
        defer { lock.unlock() }

        let prompt = """
        <|system|>
        \(systemPrompt)
        </s>
        <|user|>
        \(userPrompt)
        </s>
        <|assistant|>
        """

        guard let resolvedModelPath = resolveModelPath(preferredPath: modelPath) else {
            print("DEBUG IOSLlamaBridge: Kein gueltiger Modellpfad gefunden.")
            return ""
        }

        let llama = ensureContext(modelPath: resolvedModelPath)
        guard let llama else { return "" }

        let semaphore = DispatchSemaphore(value: 0)
        var output = ""

        Task {
            if let ctx = llama as? LlamaContext {
                await ctx.clear()
                await ctx.configureGeneration(maxTokens: maxTokens)
                await ctx.completion_init(text: prompt)

                var aggregated = ""
                while !(await ctx.is_done) {
                    let token = await ctx.completion_loop()
                    aggregated += token
                    if aggregated.count > Int(maxTokens) * 8 {
                        break
                    }
                }

                output = aggregated.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            semaphore.signal()
        }

        semaphore.wait()
        return output
#else
        print("DEBUG IOSLlamaBridge: llama-Modul nicht verlinkt, liefere leere Antwort.")
        return ""
#endif
    }

#if canImport(llama)
    fileprivate func resolveModelPath(preferredPath: String?) -> String? {
        let fileManager = FileManager.default

        let preferred = preferredPath?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !preferred.isEmpty, fileManager.fileExists(atPath: preferred) {
            return preferred
        }

        let configPath = (Bundle.main.object(forInfoDictionaryKey: "LLAMA_MODEL_PATH") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !configPath.isEmpty, fileManager.fileExists(atPath: configPath) {
            return configPath
        }

        if let bundlePath = Bundle.main.path(forResource: "model", ofType: "gguf"),
           fileManager.fileExists(atPath: bundlePath) {
            return bundlePath
        }

        if let anyBundleGguf = Bundle.main.paths(forResourcesOfType: "gguf", inDirectory: nil).first,
           fileManager.fileExists(atPath: anyBundleGguf) {
            return anyBundleGguf
        }

        if let documents = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first {
            let documentsModel = documents.appendingPathComponent("models/model.gguf").path
            if fileManager.fileExists(atPath: documentsModel) {
                return documentsModel
            }
        }

        return nil
    }

    private func ensureContext(modelPath: String) -> AnyObject? {
        if let existing = context, currentModelPath == modelPath {
            return existing
        }

        guard FileManager.default.fileExists(atPath: modelPath) else {
            print("DEBUG IOSLlamaBridge: Modellpfad existiert nicht: \(modelPath)")
            return nil
        }

        do {
            let created = try LlamaContext.create_context(path: modelPath)
            context = created as AnyObject
            currentModelPath = modelPath
            return context
        } catch {
            print("DEBUG IOSLlamaBridge: Kontext konnte nicht erzeugt werden: \(error)")
            return nil
        }
    }
#endif
}

func registerIosLlamaBridge() {
    IosLlmBridgeRegistry.shared.register(bridge: IOSLlamaBridge())
}

func registerIosLlamaBridgeIfAvailable() {
#if canImport(llama)
    let bridge = IOSLlamaBridge()

    guard let resolved = bridge.resolveModelPath(preferredPath: nil) else {
        print("DEBUG IOSLlamaBridge: Kein Modell gefunden (Config/Bundle/Documents), keine Bridge-Registrierung.")
        return
    }

    print("DEBUG IOSLlamaBridge: Bridge registriert mit Modellpfad: \(resolved)")
    IosLlmBridgeRegistry.shared.register(bridge: bridge)
#else
    print("DEBUG IOSLlamaBridge: llama-Modul nicht verfuegbar, keine Bridge-Registrierung.")
#endif
}

