#!/usr/bin/env python3
"""Regenerate the dependency-free Xcode project after adding Swift source files."""
import argparse
import hashlib
import json
from pathlib import Path

IOS = Path(__file__).resolve().parents[1]
PROJECT = IOS / "ClarIDPocket.xcodeproj"


class Ref(str):
    pass


def identifier(name):
    return Ref(hashlib.sha1(name.encode()).hexdigest()[:24].upper())


def serialize(value, depth=0):
    indent = "\t" * depth
    if isinstance(value, Ref):
        return str(value)
    if isinstance(value, dict):
        fields = [f"{indent}\t{json.dumps(str(key))} = {serialize(item, depth + 1)};" for key, item in value.items()]
        return "{\n" + "\n".join(fields) + "\n" + indent + "}"
    if isinstance(value, list):
        return "(\n" + "".join(f"{indent}\t{serialize(item, depth + 1)},\n" for item in value) + indent + ")"
    if isinstance(value, int):
        return str(value)
    return json.dumps(value, ensure_ascii=False)


def generate():
    objects = {}

    def add(object_name, isa, **fields):
        ref = identifier(object_name)
        objects[ref] = {"isa": isa, **fields}
        return ref

    sources, resources = [], []
    source_builds, resource_builds = [], []
    for path in sorted(list(IOS.glob("Core/*.swift")) + list(IOS.glob("App/*.swift"))):
        relative = str(path.relative_to(IOS))
        ref = add(relative, "PBXFileReference", lastKnownFileType="sourcecode.swift", path=relative, sourceTree="<group>")
        sources.append(ref)
        source_builds.append(add(relative + ":build", "PBXBuildFile", fileRef=ref))
    shared = "../core/src/main/resources/clarid/0.04/"
    paths = [
        ("Resources/clarid-codebook.json", "text.json"),
        (shared + "icd10.json", "text.json"),
        (shared + "icd10_order.json", "text.json"),
        (shared + "provenance.json", "text.json"),
        (shared + "LICENSE-ClarID-Tools.txt", "text"),
        (shared + "README_ICD10.md", "text"),
        ("App/PrivacyInfo.xcprivacy", "text.xml"),
        ("App/Assets.xcassets", "folder.assetcatalog"),
    ]
    for relative, file_type in paths:
        ref = add(relative, "PBXFileReference", lastKnownFileType=file_type, path=relative, sourceTree="<group>")
        resources.append(ref)
        resource_builds.append(add(relative + ":build", "PBXBuildFile", fileRef=ref))
    info = add("info", "PBXFileReference", lastKnownFileType="text.plist.xml", path="App/Info.plist", sourceTree="<group>")
    product = add("product", "PBXFileReference", explicitFileType="wrapper.application", path="ClarIDPocket.app", sourceTree="BUILT_PRODUCTS_DIR", includeInIndex=0)
    sources_group = add("sources", "PBXGroup", children=sources, name="Sources", sourceTree="<group>")
    resources_group = add("resources", "PBXGroup", children=resources, name="Resources", sourceTree="<group>")
    products_group = add("products", "PBXGroup", children=[product], name="Products", sourceTree="<group>")
    root_group = add("root", "PBXGroup", children=[sources_group, resources_group, info, products_group], sourceTree="<group>")
    source_phase = add("source-phase", "PBXSourcesBuildPhase", buildActionMask=2147483647, files=source_builds, runOnlyForDeploymentPostprocessing=0)
    resource_phase = add("resource-phase", "PBXResourcesBuildPhase", buildActionMask=2147483647, files=resource_builds, runOnlyForDeploymentPostprocessing=0)
    framework_phase = add("framework-phase", "PBXFrameworksBuildPhase", buildActionMask=2147483647, files=[], runOnlyForDeploymentPostprocessing=0)
    project_configs, target_configs = [], []
    for mode in ["Debug", "Release"]:
        project_settings = {
            "CLANG_ENABLE_MODULES": "YES", "CLANG_ENABLE_OBJC_ARC": "YES",
            "SDKROOT": "iphoneos", "IPHONEOS_DEPLOYMENT_TARGET": "15.0",
            "SWIFT_VERSION": "5.0", "SWIFT_STRICT_CONCURRENCY": "minimal",
            "DEBUG_INFORMATION_FORMAT": "dwarf" if mode == "Debug" else "dwarf-with-dsym",
            "SWIFT_OPTIMIZATION_LEVEL": "-Onone" if mode == "Debug" else "-O",
            "SWIFT_COMPILATION_MODE": "singlefile" if mode == "Debug" else "wholemodule",
            "ENABLE_TESTABILITY": "YES" if mode == "Debug" else "NO",
            "ONLY_ACTIVE_ARCH": "YES" if mode == "Debug" else "NO",
        }
        if mode == "Debug":
            project_settings["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] = "DEBUG $(inherited)"
        target_settings = {
            "PRODUCT_NAME": "ClarIDPocket", "PRODUCT_BUNDLE_IDENTIFIER": "org.cnag.clarid.pocket",
            "MARKETING_VERSION": "0.2.0", "CURRENT_PROJECT_VERSION": "2",
            "INFOPLIST_FILE": "App/Info.plist", "GENERATE_INFOPLIST_FILE": "NO",
            "CODE_SIGN_STYLE": "Automatic", "TARGETED_DEVICE_FAMILY": "1,2",
            "SUPPORTED_PLATFORMS": "iphoneos iphonesimulator", "SUPPORTS_MACCATALYST": "NO",
            "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
            "LD_RUNPATH_SEARCH_PATHS": "$(inherited) @executable_path/Frameworks",
        }
        project_configs.append(add("project:" + mode, "XCBuildConfiguration", buildSettings=project_settings, name=mode))
        target_configs.append(add("target:" + mode, "XCBuildConfiguration", buildSettings=target_settings, name=mode))
    project_config_list = add("project-configs", "XCConfigurationList", buildConfigurations=project_configs, defaultConfigurationIsVisible=0, defaultConfigurationName="Release")
    target_config_list = add("target-configs", "XCConfigurationList", buildConfigurations=target_configs, defaultConfigurationIsVisible=0, defaultConfigurationName="Release")
    target = add("target", "PBXNativeTarget", buildConfigurationList=target_config_list,
                 buildPhases=[source_phase, framework_phase, resource_phase], buildRules=[], dependencies=[],
                 name="ClarIDPocket", productName="ClarIDPocket", productReference=product, productType="com.apple.product-type.application")
    project = add("project", "PBXProject", attributes={"LastUpgradeCheck": "1600", "TargetAttributes": {target: {"CreatedOnToolsVersion": "16.0", "ProvisioningStyle": "Automatic"}}},
                  buildConfigurationList=project_config_list, compatibilityVersion="Xcode 14.0", developmentRegion="en", hasScannedForEncodings=0,
                  knownRegions=["en", "Base"], mainGroup=root_group, productRefGroup=products_group, projectDirPath="", projectRoot="", targets=[target])
    document = {"archiveVersion": 1, "classes": {}, "objectVersion": 56, "objects": objects, "rootObject": project}
    text = "// !$*UTF8*$!\n" + serialize(document) + "\n"
    scheme = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES">
    <BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">
      <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="ClarIDPocket.app" BlueprintName="ClarIDPocket" ReferencedContainer="container:ClarIDPocket.xcodeproj"/>
    </BuildActionEntry></BuildActionEntries>
  </BuildAction>
  <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables/></TestAction>
  <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES">
    <BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="ClarIDPocket.app" BlueprintName="ClarIDPocket" ReferencedContainer="container:ClarIDPocket.xcodeproj"/></BuildableProductRunnable>
  </LaunchAction>
  <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES">
    <BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="ClarIDPocket.app" BlueprintName="ClarIDPocket" ReferencedContainer="container:ClarIDPocket.xcodeproj"/></BuildableProductRunnable>
  </ProfileAction>
  <AnalyzeAction buildConfiguration="Debug"/>
  <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
'''
    return {PROJECT / "project.pbxproj": text, PROJECT / "xcshareddata/xcschemes/ClarIDPocket.xcscheme": scheme}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for path, text in generate().items():
        if args.check:
            if not path.exists() or path.read_text() != text:
                parser.exit(1, "Xcode project is stale; run ios/tools/generate_project.py\n")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
    print("Xcode project is current" if args.check else f"Wrote {PROJECT}")


if __name__ == "__main__":
    main()
