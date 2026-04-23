import Foundation
import ComposeApp

#if canImport(whisper)
import AVFoundation
import whisper
#elseif canImport(libwhisper_all)
import AVFoundation
import libwhisper_all
#endif

@objc(IOSWhisperBridge)
final class IOSWhisperBridge: NSObject, IosWhisperBridge {
    private var lastError: String = ""

    func transcribe(
        modelPath: String,
        audioFilePath: String,
        language: String
    ) -> String {
        lastError = ""

        guard let resolvedModelPath = resolveModelPath(preferredPath: modelPath) else {
            lastError = "Kein gueltiger Whisper-Modellpfad gefunden"
            print("DEBUG IOSWhisperBridge: \(lastError).")
            return ""
        }

        guard let resolvedAudioPath = resolveAudioPath(preferredPath: audioFilePath) else {
            lastError = "Audio-Datei existiert nicht: \(audioFilePath)"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return ""
        }

#if canImport(whisper) || canImport(libwhisper_all)
        do {
            return try transcribeWithWhisperModule(
                modelPath: resolvedModelPath,
                audioFilePath: resolvedAudioPath,
                language: language
            )
        } catch {
            let nsError = error as NSError
            let details = [
                "domain=\(nsError.domain)",
                "code=\(nsError.code)",
                "description=\(nsError.localizedDescription)",
            ].joined(separator: ", ")
            lastError = "Lokale Transkription fehlgeschlagen: \(details)"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return ""
        }
#else
        lastError = "whisper-Modul nicht verlinkt"
        print("DEBUG IOSWhisperBridge: \(lastError), liefere leere Antwort.")
        return ""
#endif
    }

    func lastErrorMessage() -> String {
        lastError
    }

    fileprivate func resolveModelPath(preferredPath: String?) -> String? {
        let fileManager = FileManager.default

        let preferred = preferredPath?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !preferred.isEmpty, fileManager.fileExists(atPath: preferred) {
            return preferred
        }

        let configPath = (Bundle.main.object(forInfoDictionaryKey: "WHISPER_MODEL_PATH") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !configPath.isEmpty, fileManager.fileExists(atPath: configPath) {
            return configPath
        }

        if let bundlePath = Bundle.main.path(forResource: "whisper-base", ofType: "bin"),
           fileManager.fileExists(atPath: bundlePath) {
            return bundlePath
        }

        if let anyBundleBin = Bundle.main.paths(forResourcesOfType: "bin", inDirectory: nil)
            .first(where: { $0.lowercased().contains("whisper") && fileManager.fileExists(atPath: $0) }) {
            return anyBundleBin
        }

        if let documents = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first {
            let documentsModel = documents.appendingPathComponent("models/whisper-base.bin").path
            if fileManager.fileExists(atPath: documentsModel) {
                return documentsModel
            }
        }

        return nil
    }

    fileprivate func resolveAudioPath(preferredPath: String?) -> String? {
        let fileManager = FileManager.default
        let raw = preferredPath?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if raw.isEmpty { return nil }

        if fileManager.fileExists(atPath: raw) {
            return raw
        }

        if let fileUrl = URL(string: raw), fileUrl.isFileURL {
            let path = fileUrl.path
            if fileManager.fileExists(atPath: path) {
                return path
            }
        }

        if let decoded = raw.removingPercentEncoding {
            if fileManager.fileExists(atPath: decoded) {
                return decoded
            }
            if let decodedUrl = URL(string: decoded), decodedUrl.isFileURL {
                let decodedPath = decodedUrl.path
                if fileManager.fileExists(atPath: decodedPath) {
                    return decodedPath
                }
            }
        }

        return nil
    }
}

#if canImport(whisper) || canImport(libwhisper_all)
private extension IOSWhisperBridge {
    func transcribeWithWhisperModule(
        modelPath: String,
        audioFilePath: String,
        language: String
    ) throws -> String {
        let samples = try decodeToMono16kSamples(filePath: audioFilePath)
        guard !samples.isEmpty else {
            lastError = "Keine Samples nach Dekodierung"
            print("DEBUG IOSWhisperBridge: \(lastError).")
            return ""
        }

        guard let ctx = whisper_init_from_file(modelPath) else {
            lastError = "whisper_init_from_file fehlgeschlagen: \(modelPath)"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return ""
        }
        defer { whisper_free(ctx) }

        var params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY)
        params.print_progress = false
        params.print_realtime = false
        params.print_timestamps = true
        params.translate = false
        params.n_threads = Int32(max(1, min(8, ProcessInfo.processInfo.processorCount - 1)))

        let normalizedLanguage = normalizedWhisperLanguage(language)
        var languagePointer: UnsafeMutablePointer<CChar>? = nil
        defer {
            if let languagePointer {
                free(languagePointer)
            }
        }
        if !normalizedLanguage.isEmpty {
            languagePointer = strdup(normalizedLanguage)
            params.language = UnsafePointer(languagePointer)
        } else {
            params.language = nil
        }

        let status = samples.withUnsafeBufferPointer { buffer in
            whisper_full(ctx, params, buffer.baseAddress, Int32(buffer.count))
        }
        guard status == 0 else {
            lastError = "whisper_full fehlgeschlagen mit Status \(status)"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return ""
        }

        let count = whisper_full_n_segments(ctx)
        guard count > 0 else {
            lastError = "whisper_full lieferte 0 Segmente"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return ""
        }

        var lines: [String] = []
        for i in 0..<count {
            let t0 = whisper_full_get_segment_t0(ctx, i)
            let t1 = whisper_full_get_segment_t1(ctx, i)
            guard let textPtr = whisper_full_get_segment_text(ctx, i) else { continue }

            let text = String(cString: textPtr).trimmingCharacters(in: .whitespacesAndNewlines)
            if text.isEmpty { continue }

            let start = formatWhisperTimestamp(t0)
            let end = formatWhisperTimestamp(t1)
            lines.append("[\(start) --> \(end)]: \(text)")
        }

        return lines.joined(separator: "\n")
    }

    func decodeToMono16kSamples(filePath: String) throws -> [Float] {
        let url = URL(fileURLWithPath: filePath)
        let fileSizeBytes = (try? FileManager.default.attributesOfItem(atPath: filePath)[.size] as? NSNumber)?.int64Value
        if let fileSizeBytes {
            print("DEBUG IOSWhisperBridge: Audio-Dateigroesse=\(fileSizeBytes) bytes")
        }

        do {
            let samples = try decodeWithAVAudioFile(url: url)
            if !samples.isEmpty {
                return samples
            }
            print("DEBUG IOSWhisperBridge: AVAudioFile-Dekodierung lieferte 0 Samples, versuche AVAssetReader-Fallback.")
        } catch {
            let nsError = error as NSError
            print(
                "DEBUG IOSWhisperBridge: AVAudioFile-Dekodierung fehlgeschlagen " +
                    "(domain=\(nsError.domain), code=\(nsError.code), description=\(nsError.localizedDescription)); " +
                    "versuche AVAssetReader-Fallback."
            )
        }

        let fallback = decodeWithAVAssetReader(url: url)
        if !fallback.isEmpty {
            return fallback
        }

        if lastError.isEmpty {
            lastError = "Keine Samples nach Dekodierung (AVAudioFile + AVAssetReader)"
        }
        return []
    }

    private func decodeWithAVAudioFile(url: URL) throws -> [Float] {
        let source = try AVAudioFile(forReading: url)

        guard source.processingFormat.sampleRate > 0, source.processingFormat.channelCount > 0 else {
            lastError = "Audio-Format ungueltig (sampleRate=\(source.processingFormat.sampleRate), channels=\(source.processingFormat.channelCount))"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return []
        }

        guard let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: 16_000,
            channels: 1,
            interleaved: false
        ) else {
            lastError = "Ziel-Audioformat konnte nicht erstellt werden"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return []
        }

        let converter = AVAudioConverter(from: source.processingFormat, to: targetFormat)
        guard let converter else {
            print("DEBUG IOSWhisperBridge: AVAudioConverter konnte nicht erstellt werden.")
            return []
        }

        let frameCapacity: AVAudioFrameCount = 4096
        guard let inputBuffer = AVAudioPCMBuffer(
            pcmFormat: source.processingFormat,
            frameCapacity: frameCapacity
        ) else {
            lastError = "Input-Audiopuffer konnte nicht erstellt werden"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return []
        }

        let ratio = targetFormat.sampleRate / source.processingFormat.sampleRate
        var output: [Float] = []
        var reachedEndOfInput = false

        while true {
            if !reachedEndOfInput {
                do {
                    try source.read(into: inputBuffer)
                } catch {
                    let nsError = error as NSError
                    lastError = "Audio-Lesen fehlgeschlagen (domain=\(nsError.domain), code=\(nsError.code), description=\(nsError.localizedDescription))"
                    print("DEBUG IOSWhisperBridge: \(lastError)")
                    return []
                }
                reachedEndOfInput = inputBuffer.frameLength == 0
            }

            let inputFrameLength = Int(inputBuffer.frameLength)
            let outputCapacity = reachedEndOfInput
                ? Int(frameCapacity)
                : max(1, Int(Double(inputFrameLength) * ratio) + 8)

            guard let converted = AVAudioPCMBuffer(
                pcmFormat: targetFormat,
                frameCapacity: AVAudioFrameCount(outputCapacity)
            ) else {
                return []
            }

            var didFeedInput = false
            var conversionError: NSError?
            let status = converter.convert(to: converted, error: &conversionError) { _, outStatus in
                if didFeedInput {
                    outStatus.pointee = reachedEndOfInput ? .endOfStream : .noDataNow
                    return nil
                }

                didFeedInput = true
                if reachedEndOfInput {
                    outStatus.pointee = .endOfStream
                    return nil
                }

                outStatus.pointee = .haveData
                return inputBuffer
            }

            if status == .error {
                if let conversionError {
                    lastError = "Audio-Konvertierung fehlgeschlagen (domain=\(conversionError.domain), code=\(conversionError.code), description=\(conversionError.localizedDescription))"
                } else {
                    lastError = "Audio-Konvertierung fehlgeschlagen (ohne NSError-Details)"
                }
                print("DEBUG IOSWhisperBridge: \(lastError).")
                return []
            }

            let frameLength = Int(converted.frameLength)
            if frameLength > 0, let channelData = converted.floatChannelData?.pointee {
                output.append(contentsOf: UnsafeBufferPointer(start: channelData, count: frameLength))
            }

            if reachedEndOfInput && status == .endOfStream {
                break
            }
        }

        return output
    }

    private func normalizedWhisperLanguage(_ rawLanguage: String) -> String {
        let normalized = rawLanguage
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()

        guard !normalized.isEmpty else {
            return ""
        }

        if normalized == "auto" {
            return ""
        }

        let prefix = String(normalized.prefix(2))
        let candidate = prefix.count == 2 ? prefix : normalized
        let languageId = whisper_lang_id(candidate)
        guard languageId >= 0 else {
            print("DEBUG IOSWhisperBridge: Ungueltige Sprache '\(rawLanguage)', nutze Auto-Erkennung.")
            return ""
        }

        return candidate
    }

    private func decodeWithAVAssetReader(url: URL) -> [Float] {
        let asset = AVURLAsset(url: url)
        guard let track = asset.tracks(withMediaType: .audio).first else {
            lastError = "AVAssetReader: Kein Audio-Track gefunden"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return []
        }

        let outputSettings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 32,
            AVLinearPCMIsFloatKey: true,
            AVLinearPCMIsNonInterleaved: false,
            AVLinearPCMIsBigEndianKey: false,
        ]

        let reader: AVAssetReader
        do {
            reader = try AVAssetReader(asset: asset)
        } catch {
            let nsError = error as NSError
            lastError = "AVAssetReader-Init fehlgeschlagen (domain=\(nsError.domain), code=\(nsError.code), description=\(nsError.localizedDescription))"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return []
        }

        let trackOutput = AVAssetReaderTrackOutput(track: track, outputSettings: outputSettings)
        trackOutput.alwaysCopiesSampleData = false
        guard reader.canAdd(trackOutput) else {
            lastError = "AVAssetReader kann TrackOutput nicht hinzufuegen"
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return []
        }
        reader.add(trackOutput)

        guard reader.startReading() else {
            let nsError = reader.error as NSError?
            if let nsError {
                lastError = "AVAssetReader startReading fehlgeschlagen (domain=\(nsError.domain), code=\(nsError.code), description=\(nsError.localizedDescription))"
            } else {
                lastError = "AVAssetReader startReading fehlgeschlagen"
            }
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return []
        }

        var samples: [Float] = []
        while let sampleBuffer = trackOutput.copyNextSampleBuffer() {
            defer { CMSampleBufferInvalidate(sampleBuffer) }
            guard let blockBuffer = CMSampleBufferGetDataBuffer(sampleBuffer) else { continue }

            var length = 0
            var dataPointer: UnsafeMutablePointer<Int8>?
            let status = CMBlockBufferGetDataPointer(
                blockBuffer,
                atOffset: 0,
                lengthAtOffsetOut: nil,
                totalLengthOut: &length,
                dataPointerOut: &dataPointer
            )

            guard status == kCMBlockBufferNoErr, let dataPointer, length > 0 else { continue }

            let floatCount = length / MemoryLayout<Float>.size
            let typedPointer = UnsafeRawPointer(dataPointer).bindMemory(to: Float.self, capacity: floatCount)
            samples.append(contentsOf: UnsafeBufferPointer(start: typedPointer, count: floatCount))
        }

        if reader.status == .failed {
            let nsError = reader.error as NSError?
            if let nsError {
                lastError = "AVAssetReader fehlgeschlagen (domain=\(nsError.domain), code=\(nsError.code), description=\(nsError.localizedDescription))"
            } else {
                lastError = "AVAssetReader fehlgeschlagen"
            }
            print("DEBUG IOSWhisperBridge: \(lastError)")
            return []
        }

        print("DEBUG IOSWhisperBridge: AVAssetReader-Fallback Samples=\(samples.count)")
        return samples
    }

    func formatWhisperTimestamp(_ centiseconds: Int64) -> String {
        let totalMillis = centiseconds * 10
        let hours = Int(totalMillis / 3_600_000)
        let minutes = Int((totalMillis % 3_600_000) / 60_000)
        let seconds = Int((totalMillis % 60_000) / 1_000)
        let millis = Int(totalMillis % 1_000)
        return String(format: "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
#endif

func registerIosWhisperBridge() {
    IosWhisperBridgeRegistry.shared.register(bridge: IOSWhisperBridge())
}

func registerIosWhisperBridgeIfAvailable() {
    let bridge = IOSWhisperBridge()
    IosWhisperBridgeRegistry.shared.register(bridge: bridge)

#if canImport(whisper)
    print("DEBUG IOSWhisperBridge: Swift-Modul 'whisper' erkannt.")
#elseif canImport(libwhisper_all)
    print("DEBUG IOSWhisperBridge: Swift-Modul 'libwhisper_all' erkannt.")
#else
    print("DEBUG IOSWhisperBridge: Kein Swift-Whisper-Modul erkannt (weder 'whisper' noch 'libwhisper_all').")
#endif

    if let resolved = bridge.resolveModelPath(preferredPath: nil) {
        print("DEBUG IOSWhisperBridge: Bridge registriert mit Modellpfad: \(resolved)")
    } else {
        print("DEBUG IOSWhisperBridge: Bridge registriert, aber noch kein Modell gefunden (Config/Bundle/Documents).")
    }
}
