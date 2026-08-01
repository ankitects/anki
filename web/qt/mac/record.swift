// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation
import AVKit

enum RecordError: Error {
    case noPermission
    case audioFormat
    case recordInvoke
    case stoppedWithFailure
    case encodingFailure
}

@_cdecl("start_wav_record")
public func startWavRecord(
    path: UnsafePointer<CChar>,
    onError: @escaping @convention(c) (UnsafePointer<CChar>) -> Void
) {
    let url = URL(fileURLWithPath: String(cString: path))
    AudioRecorder.shared.beginRecording(url: url, onError: { error in
        error.localizedDescription.withCString { cString in
            onError(cString)
        }
    })
}

@_cdecl("end_wav_record")
public func endWavRecord() {
    AudioRecorder.shared.endRecording()
}


private class AudioRecorder: NSObject, AVAudioRecorderDelegate {
    static let shared = AudioRecorder()

    private var audioRecorder: AVAudioRecorder?
    private var onError: ((RecordError) -> Void)?

    func beginRecording(url: URL, onError: @escaping (Error) -> Void) {
        self.endRecording()

        requestPermission { success in
            if !success {
                onError(RecordError.noPermission)
                return
            }

            do {
                try self.beginRecordingInner(url: url)
            } catch {
                onError(error)
                return
            }
            self.onError = onError
        }

    }

    func endRecording() {
        if let recorder = audioRecorder {
            recorder.stop()
        }
        audioRecorder = nil
        onError = nil
    }

    /// Request permission, then call provided callback (true on success).
    private func requestPermission(completionHandler: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
            case .notDetermined:
                AVCaptureDevice.requestAccess(
                    for: .audio,
                    completionHandler: completionHandler
                )
                return
            case .authorized:
                completionHandler(true)
                return
            case .restricted:
                print("recording restricted")
            case .denied:
                print("recording denied")
            @unknown default:
                print("recording unknown permission")
        }
        completionHandler(false)
    }

    private func beginRecordingInner(url: URL) throws {
        guard let audioFormat = AVAudioFormat.init(
            commonFormat: .pcmFormatInt16,
            sampleRate: 44100,
            channels: 1,
            interleaved: true
        ) else {
            throw RecordError.audioFormat
        }
        let recorder = try AVAudioRecorder(url: url, format: audioFormat)
        if !recorder.record() {
            throw RecordError.recordInvoke
        }
        audioRecorder = recorder
    }


    func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        if !flag {
            onError?(.stoppedWithFailure)
        }
    }


    func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        onError?(.encodingFailure)
    }
}
