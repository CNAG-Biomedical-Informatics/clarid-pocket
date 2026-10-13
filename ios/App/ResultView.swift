import SwiftUI

struct ResultView: View {
    let result: DecodedIdentifier
    let identifier: String
    let settings: DecodeSettings
    private var accent: Color { result.record.entity == .subject ? PocketTheme.blue : PocketTheme.teal }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 14) {
                    Image(systemName: "checkmark.circle.fill").font(.largeTitle).foregroundColor(.white)
                        .accessibilityLabel("Scan successful")
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(settings.entity == .auto ? "Detected" : "Decoded") \(result.record.entity.rawValue.lowercased())")
                            .font(.title2.weight(.bold))
                        let width = result.format == .human ? settings.humanSubjectWidth : settings.stubSubjectWidth
                        Text("\(result.format.rawValue) · Subject width \(width)").font(.body)
                    }
                }
            }
            .foregroundColor(.white).padding(20).frame(maxWidth: .infinity, alignment: .leading)
            .background(accent).clipShape(RoundedRectangle(cornerRadius: 20))

            PocketCard {
                VStack(alignment: .leading, spacing: 20) {
                    switch result.record {
                    case .biosample(let value):
                        term("Project", "building.2", value.project)
                        term("Species", "leaf", value.species)
                        field("Subject ID", "person.crop.circle", String(value.subjectID))
                        term("Tissue", "square.stack.3d.up", value.tissue)
                        term("Sample type", "testtube.2", value.sampleType)
                        term("Assay", "waveform.path.ecg", value.assay)
                        diseases(value.conditions)
                        term("Timepoint", "clock", value.timepoint)
                        field("Duration", "hourglass", value.duration)
                        if let batch = value.batch { field("Batch", "tray.2", String(batch)) }
                        if let replicate = value.replicate { field("Replicate", "square.on.square", String(replicate)) }
                    case .subject(let value):
                        field("Study", "building.2", value.study, value.studyLabel)
                        field("Subject ID", "person.crop.circle", String(value.subjectID))
                        term("Type", "person.text.rectangle", value.type)
                        diseases(value.conditions)
                        term("Sex", "person.2", value.sex)
                        term("Age group", "calendar", value.ageGroup)
                    }
                }
                .textSelection(.enabled)
            }
            PocketCard {
                VStack(alignment: .leading, spacing: 10) {
                    Label("Identifier", systemImage: "qrcode").font(.body.weight(.semibold)).foregroundColor(.secondary)
                    Text(identifier).font(.body.monospaced()).textSelection(.enabled)
                }
            }
        }
    }

    private func term(_ title: String, _ icon: String, _ value: Term) -> some View {
        field(title, icon, value.code, value.label)
    }
    private func field(_ title: String, _ icon: String, _ value: String, _ label: String? = nil) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon).font(.title3).foregroundColor(accent)
                .frame(width: 40, height: 40).background(accent.opacity(0.10))
                .clipShape(RoundedRectangle(cornerRadius: 12)).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 5) {
                Text(title).font(.body).foregroundColor(.secondary)
                Text(value).font(.title3.weight(.semibold))
                if let label = label, label != value { Text(label).font(.body) }
            }
            Spacer(minLength: 0)
        }
    }
    private func diseases(_ values: [Disease]) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(Array(values.enumerated()), id: \.offset) { index, value in
                field(values.count == 1 ? "Disease" : "Disease \(index + 1)", "heart.text.square", value.code, value.label)
            }
        }
    }
}
