import SwiftUI
import AVFoundation
import UIKit

enum ScannerStatus: Equatable {
    case starting, ready, ambiguous, denied, unavailable(String)
}

struct ScannerView: View {
    let onCapture: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var status: ScannerStatus = .starting
    @State private var torch = false
    @State private var torchAvailable = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            CameraPreview(status: $status, torchAvailable: $torchAvailable, torch: torch, onCapture: onCapture).ignoresSafeArea()
            if status == .ready || status == .ambiguous {
                GeometryReader { geometry in
                    let side = min(geometry.size.width, geometry.size.height) * 0.74
                    RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.75), lineWidth: 2)
                        .frame(width: side, height: side).position(x: geometry.size.width / 2, y: geometry.size.height / 2)
                    Image(systemName: "plus").font(.title).foregroundColor(.white)
                        .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
                }.ignoresSafeArea().accessibilityHidden(true).allowsHitTesting(false)
            }
            VStack {
                HStack {
                    Text("Scan QR").font(.title2.weight(.bold)).foregroundColor(.white)
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.title3.weight(.semibold)).frame(width: 56, height: 56)
                    }
                    .foregroundColor(.white).background(Color.black.opacity(0.65))
                    .clipShape(Circle()).accessibilityLabel("Close scanner")
                }
                Spacer()
                statusPanel
                if torchAvailable && (status == .ready || status == .ambiguous) {
                    Button { torch.toggle() } label: {
                        Label(torch ? "Light off" : "Light on", systemImage: torch ? "flashlight.off.fill" : "flashlight.on.fill")
                            .font(.body.weight(.semibold)).padding(.horizontal, 24).frame(minHeight: 56)
                    }
                    .foregroundColor(.white).background(Color.black.opacity(0.7)).clipShape(Capsule())
                }
            }.padding(20)
        }
        .preferredColorScheme(.dark)
    }

    @ViewBuilder private var statusPanel: some View {
        switch status {
        case .starting:
            HStack(spacing: 12) { ProgressView().tint(.white); Text("Starting camera…") }.padding(20)
        case .ready, .ambiguous:
            Text(status == .ambiguous ? "Place the centre over one QR code" : "Point the camera at a QR code")
                .font(.body.weight(.medium)).multilineTextAlignment(.center).padding(20)
                .foregroundColor(.white).background(Color.black.opacity(0.7)).clipShape(RoundedRectangle(cornerRadius: 16))
        case .denied:
            scannerError("Camera access is needed to scan QR codes.", settings: true)
        case .unavailable(let message):
            scannerError(message, settings: false)
        }
    }
    private func scannerError(_ message: String, settings: Bool) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "camera.fill").font(.largeTitle)
            Text(message).font(.title3).multilineTextAlignment(.center)
            if settings {
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }.font(.body.weight(.semibold)).frame(minHeight: 48)
            }
        }
        .foregroundColor(.white).padding(24).frame(maxWidth: .infinity)
        .background(PocketTheme.navy).clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct CameraPreview: UIViewControllerRepresentable {
    @Binding var status: ScannerStatus
    @Binding var torchAvailable: Bool
    let torch: Bool
    let onCapture: (String) -> Void

    func makeUIViewController(context: Context) -> CameraController {
        let controller = CameraController()
        controller.onStatus = { status = $0 }
        controller.onTorchAvailability = { torchAvailable = $0 }
        controller.onCapture = onCapture
        return controller
    }
    func updateUIViewController(_ controller: CameraController, context: Context) {
        controller.setTorch(torch)
    }
    static func dismantleUIViewController(_ controller: CameraController, coordinator: ()) {
        controller.close()
    }
}

private final class PreviewSurface: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}

@MainActor
private final class CameraController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onStatus: ((ScannerStatus) -> Void)?
    var onTorchAvailability: ((Bool) -> Void)?
    var onCapture: ((String) -> Void)?
    private let surface = PreviewSurface()
    private let capture = CameraCapture()
    private var target = ScanTarget()
    private var visible = false

    override func loadView() { view = surface }
    override func viewDidLoad() {
        super.viewDidLoad()
        surface.preview.session = capture.session
        surface.preview.videoGravity = .resizeAspectFill
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(focus(_:))))
        view.addGestureRecognizer(UIPinchGestureRecognizer(target: self, action: #selector(zoom(_:))))
        NotificationCenter.default.addObserver(self, selector: #selector(background), name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(foreground), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(runtimeError(_:)), name: .AVCaptureSessionRuntimeError, object: capture.session)
        NotificationCenter.default.addObserver(self, selector: #selector(interrupted), name: .AVCaptureSessionWasInterrupted, object: capture.session)
        NotificationCenter.default.addObserver(self, selector: #selector(foreground), name: .AVCaptureSessionInterruptionEnded, object: capture.session)
    }
    deinit { NotificationCenter.default.removeObserver(self) }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated); visible = true; authorize()
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated); close()
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews(); updateOrientation()
    }
    private func updateOrientation() {
        guard let orientation = view.window?.windowScene?.interfaceOrientation,
              let connection = surface.preview.connection, connection.isVideoOrientationSupported else { return }
        switch orientation {
        case .portrait: connection.videoOrientation = .portrait
        case .portraitUpsideDown: connection.videoOrientation = .portraitUpsideDown
        case .landscapeLeft: connection.videoOrientation = .landscapeLeft
        case .landscapeRight: connection.videoOrientation = .landscapeRight
        default: break
        }
    }
    private func authorize() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: start()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] allowed in
                DispatchQueue.main.async {
                    guard let self = self, self.visible else { return }
                    if allowed { self.start() } else { self.onStatus?(.denied) }
                }
            }
        default: onStatus?(.denied)
        }
    }
    private func start() {
        guard visible, UIApplication.shared.applicationState == .active else { return }
        onStatus?(.starting)
        capture.start(delegate: self) { [weak self] result in
            guard let self = self, self.visible else { return }
            switch result {
            case .success(let hasTorch):
                self.onTorchAvailability?(hasTorch)
                self.updateOrientation(); self.onStatus?(.ready)
            case .failure:
                self.onStatus?(.unavailable("The camera is unavailable. Close the scanner and try again."))
            }
        }
    }
    func close() { visible = false; capture.stop(resetTorch: true) }
    @objc private func background() { capture.stop(resetTorch: false) }
    @objc private func foreground() {
        DispatchQueue.main.async { if self.visible { self.authorize() } }
    }
    @objc private func interrupted() {
        DispatchQueue.main.async {
            if self.visible { self.onStatus?(.unavailable("Camera interrupted. Scanning resumes when the camera is available.")) }
        }
    }
    @objc private func runtimeError(_ notification: Notification) {
        let error = notification.userInfo?[AVCaptureSessionErrorKey] as? AVError
        DispatchQueue.main.async {
            guard self.visible else { return }
            if error?.code == .mediaServicesWereReset { self.start() }
            else { self.onStatus?(.unavailable("The camera stopped. Close the scanner and try again.")) }
        }
    }
    func setTorch(_ enabled: Bool) { capture.setTorch(enabled) }
    @objc private func focus(_ gesture: UITapGestureRecognizer) {
        capture.focus(at: surface.preview.captureDevicePointConverted(fromLayerPoint: gesture.location(in: surface)))
    }
    @objc private func zoom(_ gesture: UIPinchGestureRecognizer) {
        let scale = gesture.scale; gesture.scale = 1; capture.zoom(by: scale)
    }

    nonisolated func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
        DispatchQueue.main.async { self.handleMetadata(objects) }
    }
    private func handleMetadata(_ objects: [AVMetadataObject]) {
        guard visible, UIApplication.shared.applicationState == .active else { return }
        let candidates = objects.compactMap { object -> ScanCandidate? in
            guard let qr = object as? AVMetadataMachineReadableCodeObject, qr.type == .qr,
                  let transformed = surface.preview.transformedMetadataObject(for: qr) else { return nil }
            let bounds = transformed.bounds
            return ScanCandidate(text: qr.stringValue, bounds: ScanBounds(left: Double(bounds.minX), top: Double(bounds.minY),
                                                                        right: Double(bounds.maxX), bottom: Double(bounds.maxY)))
        }
        let feedback = target.update(candidates, width: Double(surface.bounds.width), height: Double(surface.bounds.height))
        if let value = feedback.value { close(); onCapture?(value) }
        else { onStatus?(feedback.ambiguous ? .ambiguous : .ready) }
    }
}

/// All capture configuration and device state live on one serial queue, outside UIKit's main actor.
private final class CameraCapture {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "org.cnag.clarid.pocket.camera", qos: .userInitiated)
    private var configured = false
    private var wanted = false
    private var device: AVCaptureDevice?
    private var desiredTorch = false

    func start(delegate: AVCaptureMetadataOutputObjectsDelegate, completion: @escaping @MainActor (Result<Bool, Error>) -> Void) {
        queue.async {
            self.wanted = true
            do {
                if !self.configured { try self.configure(delegate: delegate) }
                if !self.session.isRunning { self.session.startRunning() }
                self.applyTorch()
                let hasTorch = self.device?.hasTorch ?? false
                DispatchQueue.main.async { completion(.success(hasTorch)) }
            } catch {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }
    private func configure(delegate: AVCaptureMetadataOutputObjectsDelegate) throws {
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            throw CameraError.unavailable
        }
        let input = try AVCaptureDeviceInput(device: camera)
        let output = AVCaptureMetadataOutput()
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        if session.canSetSessionPreset(.high) { session.sessionPreset = .high }
        guard session.canAddInput(input) else { throw CameraError.unavailable }
        session.addInput(input)
        guard session.canAddOutput(output) else { session.removeInput(input); throw CameraError.unavailable }
        session.addOutput(output)
        guard output.availableMetadataObjectTypes.contains(.qr) else {
            session.removeOutput(output); session.removeInput(input); throw CameraError.unavailable
        }
        output.setMetadataObjectsDelegate(delegate, queue: .main)
        output.metadataObjectTypes = [.qr]
        device = camera; configured = true
    }
    func stop(resetTorch: Bool) {
        queue.async {
            self.wanted = false
            if resetTorch { self.desiredTorch = false }
            self.applyTorch()
            if self.session.isRunning { self.session.stopRunning() }
        }
    }
    func setTorch(_ enabled: Bool) {
        queue.async { self.desiredTorch = enabled; self.applyTorch() }
    }
    private func applyTorch() {
        guard let device = device, device.hasTorch, device.isTorchAvailable else { return }
        do {
            try device.lockForConfiguration(); defer { device.unlockForConfiguration() }
            device.torchMode = desiredTorch && wanted ? .on : .off
        } catch { /* A torch is optional, including on iPads without one. */ }
    }
    func focus(at point: CGPoint) {
        queue.async {
            guard let device = self.device else { return }
            do {
                try device.lockForConfiguration(); defer { device.unlockForConfiguration() }
                if device.isFocusPointOfInterestSupported && device.isFocusModeSupported(.autoFocus) {
                    device.focusPointOfInterest = point; device.focusMode = .autoFocus
                }
                if device.isExposurePointOfInterestSupported && device.isExposureModeSupported(.continuousAutoExposure) {
                    device.exposurePointOfInterest = point; device.exposureMode = .continuousAutoExposure
                }
            } catch { }
        }
    }
    func zoom(by scale: CGFloat) {
        queue.async {
            guard let device = self.device else { return }
            do {
                try device.lockForConfiguration(); defer { device.unlockForConfiguration() }
                device.videoZoomFactor = min(max(device.videoZoomFactor * scale, device.minAvailableVideoZoomFactor), min(8, device.maxAvailableVideoZoomFactor))
            } catch { }
        }
    }
    private enum CameraError: Error { case unavailable }
}
