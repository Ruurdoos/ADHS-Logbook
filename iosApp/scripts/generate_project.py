from pathlib import Path
import json
files=sorted(Path('iosApp/Logbook').glob('*.swift'))
uid=lambda n:f'{n:024X}'
objects=[]
def obj(n,body): objects.append(uid(n)+' = { '+body+' };')
for i,f in enumerate(files):
    obj(100+i,'isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = '+f.name+'; sourceTree = "<group>";')
    obj(200+i,'isa = PBXBuildFile; fileRef = '+uid(100+i)+';')
obj(1,'isa = PBXProject; attributes = { LastUpgradeCheck = 2620; }; buildConfigurationList = '+uid(10)+'; compatibilityVersion = "Xcode 14.0"; developmentRegion = en; knownRegions = (en,de,Base); mainGroup = '+uid(2)+'; productRefGroup = '+uid(4)+'; projectDirPath = ""; targets = ('+uid(5)+','+uid(30)+','+uid(60)+','+uid(500)+');')
obj(2,'isa = PBXGroup; children = ('+uid(3)+','+uid(4)+','+uid(31)+','+uid(61)+','+uid(501)+'); sourceTree = "<group>";')
obj(3,'isa = PBXGroup; children = ('+','.join(uid(100+i) for i in range(len(files)))+'); path = Logbook; sourceTree = "<group>";')
obj(4,'isa = PBXGroup; children = ('+uid(6)+','+uid(32)+','+uid(62)+','+uid(502)+'); name = Products; sourceTree = "<group>";')
obj(5,'isa = PBXNativeTarget; buildConfigurationList = '+uid(11)+'; buildPhases = ('+uid(7)+','+uid(8)+','+uid(9)+','+uid(520)+','+uid(506)+'); buildRules = (); dependencies = ('+uid(507)+'); name = Logbook; productName = Logbook; productReference = '+uid(6)+'; productType = "com.apple.product-type.application";')
obj(6,'isa = PBXFileReference; explicitFileType = wrapper.application; path = Logbook.app; sourceTree = BUILT_PRODUCTS_DIR;')
script='set -eu\ncd "$SRCROOT/.."\nexport JAVA_HOME="$(/usr/libexec/java_home -v 21)"\nif [ "$PLATFORM_NAME" = "iphonesimulator" ]; then target=IosSimulatorArm64; else target=IosArm64; fi\nif [ "$CONFIGURATION" = "Release" ]; then mode=Release; else mode=Debug; fi\n./gradlew -PenableIos=true ":shared:link${mode}Framework${target}"\n'
obj(7,'isa = PBXShellScriptBuildPhase; alwaysOutOfDate = 1; buildActionMask = 2147483647; files = (); inputPaths = (); outputPaths = (); runOnlyForDeploymentPostprocessing = 0; shellPath = /bin/sh; shellScript = '+json.dumps(script)+';')
obj(8,'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ('+','.join(uid(200+i) for i in range(len(files)))+'); runOnlyForDeploymentPostprocessing = 0;')
obj(9,'isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0;')
for n,configs in [(10,[12,13]),(11,[14,15])]: obj(n,'isa = XCConfigurationList; buildConfigurations = ('+','.join(map(uid,configs))+'); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;')
for n,name in [(12,'Debug'),(13,'Release')]: obj(n,'isa = XCBuildConfiguration; name = '+name+'; buildSettings = { '+('SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG; ' if name == 'Debug' else '')+'CLANG_ENABLE_MODULES = YES; SDKROOT = iphoneos; IPHONEOS_DEPLOYMENT_TARGET = 17.0; };')
for n,name,mode in [(14,'Debug','debug'),(15,'Release','release')]:
    settings='''PRODUCT_BUNDLE_IDENTIFIER = com.adhs.logbook.quietsage; PRODUCT_NAME = "$(TARGET_NAME)"; SWIFT_VERSION = 5.0;
GENERATE_INFOPLIST_FILE = YES; INFOPLIST_FILE = Logbook/Info.plist; INFOPLIST_KEY_CFBundleDisplayName = "ADHS Logbook"; INFOPLIST_KEY_UILaunchScreen_Generation = YES;
INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES; INFOPLIST_KEY_UISupportedInterfaceOrientations = "UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
"ARCHS[sdk=iphonesimulator*]" = arm64; TARGETED_DEVICE_FAMILY = "1,2"; SUPPORTED_PLATFORMS = "iphoneos iphonesimulator"; ENABLE_USER_SCRIPT_SANDBOXING = NO; CODE_SIGN_STYLE = Automatic; CODE_SIGN_ENTITLEMENTS = Logbook/Logbook.entitlements;
CURRENT_PROJECT_VERSION = 4; MARKETING_VERSION = 0.4.0; SWIFT_EMIT_LOC_STRINGS = NO; ENABLE_TESTABILITY = YES;
"KOTLIN_TARGET[sdk=iphonesimulator*]" = iosSimulatorArm64; "KOTLIN_TARGET[sdk=iphoneos*]" = iosArm64;
FRAMEWORK_SEARCH_PATHS = "$(SRCROOT)/../shared/build/bin/$(KOTLIN_TARGET)/'''+mode+'''Framework";
OTHER_LDFLAGS = ("$(inherited)","-framework",LogbookShared,"-lsqlite3"); LD_RUNPATH_SEARCH_PATHS = "$(inherited) @executable_path/Frameworks";'''
    obj(n,'isa = XCBuildConfiguration; name = '+name+'; buildSettings = { '+settings+' };')

obj(30,'isa = PBXNativeTarget; buildConfigurationList = '+uid(33)+'; buildPhases = ('+uid(34)+','+uid(35)+'); dependencies = ('+uid(36)+'); name = LogbookTests; productName = LogbookTests; productReference = '+uid(32)+'; productType = "com.apple.product-type.bundle.unit-test";')
obj(31,'isa = PBXGroup; children = ('+uid(38)+','+uid(39)+'); path = LogbookTests; sourceTree = "<group>";')
obj(32,'isa = PBXFileReference; explicitFileType = wrapper.cfbundle; path = LogbookTests.xctest; sourceTree = BUILT_PRODUCTS_DIR;')
obj(33,'isa = XCConfigurationList; buildConfigurations = ('+uid(42)+','+uid(43)+'); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;')
obj(34,'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ('+uid(40)+'); runOnlyForDeploymentPostprocessing = 0;')
obj(35,'isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ('+uid(41)+','+uid(527)+','+uid(536)+'); runOnlyForDeploymentPostprocessing = 0;')
obj(36,'isa = PBXTargetDependency; target = '+uid(5)+'; targetProxy = '+uid(37)+';')
obj(37,'isa = PBXContainerItemProxy; containerPortal = '+uid(1)+'; proxyType = 1; remoteGlobalIDString = '+uid(5)+'; remoteInfo = Logbook;')
obj(38,'isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = MustTests.swift; sourceTree = "<group>";')
obj(39,'isa = PBXFileReference; lastKnownFileType = file; path = android-backup.adhsbak; sourceTree = "<group>";')
obj(40,'isa = PBXBuildFile; fileRef = '+uid(38)+';')
obj(41,'isa = PBXBuildFile; fileRef = '+uid(39)+';')
for n,name in [(42,'Debug'),(43,'Release')]:
    obj(n,'isa = XCBuildConfiguration; name = '+name+'; buildSettings = { PRODUCT_NAME = "$(TARGET_NAME)"; PRODUCT_BUNDLE_IDENTIFIER = com.adhs.logbook.tests; GENERATE_INFOPLIST_FILE = YES; SWIFT_VERSION = 5.0; "ARCHS[sdk=iphonesimulator*]" = arm64; TARGETED_DEVICE_FAMILY = "1,2"; TEST_HOST = "$(BUILT_PRODUCTS_DIR)/Logbook.app/Logbook"; BUNDLE_LOADER = "$(TEST_HOST)"; FRAMEWORK_SEARCH_PATHS = "$(SRCROOT)/../shared/build/bin/iosSimulatorArm64/debugFramework"; };')


obj(60,'isa = PBXNativeTarget; buildConfigurationList = '+uid(63)+'; buildPhases = ('+uid(64)+'); dependencies = ('+uid(36)+'); name = LogbookUITests; productName = LogbookUITests; productReference = '+uid(62)+'; productType = "com.apple.product-type.bundle.ui-testing";')
obj(61,'isa = PBXGroup; children = ('+uid(65)+'); path = LogbookUITests; sourceTree = "<group>";')
obj(62,'isa = PBXFileReference; explicitFileType = wrapper.cfbundle; path = LogbookUITests.xctest; sourceTree = BUILT_PRODUCTS_DIR;')
obj(63,'isa = XCConfigurationList; buildConfigurations = ('+uid(67)+','+uid(68)+'); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;')
obj(64,'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ('+uid(66)+'); runOnlyForDeploymentPostprocessing = 0;')
obj(65,'isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = LoggingUITests.swift; sourceTree = "<group>";')
obj(66,'isa = PBXBuildFile; fileRef = '+uid(65)+';')
for n,name in [(67,'Debug'),(68,'Release')]:
    obj(n,'isa = XCBuildConfiguration; name = '+name+'; buildSettings = { PRODUCT_NAME = "$(TARGET_NAME)"; PRODUCT_BUNDLE_IDENTIFIER = com.adhs.logbook.uitests; GENERATE_INFOPLIST_FILE = YES; SWIFT_VERSION = 5.0; "ARCHS[sdk=iphonesimulator*]" = arm64; TARGETED_DEVICE_FAMILY = "1,2"; TEST_TARGET_NAME = Logbook; };')

obj(500,'isa = PBXNativeTarget; buildConfigurationList = '+uid(503)+'; buildPhases = ('+uid(504)+'); dependencies = (); name = LogbookWidget; productName = LogbookWidget; productReference = '+uid(502)+'; productType = "com.apple.product-type.app-extension";')
obj(501,'isa = PBXGroup; children = ('+uid(509)+'); path = LogbookWidget; sourceTree = "<group>";')
obj(502,'isa = PBXFileReference; explicitFileType = "wrapper.app-extension"; path = LogbookWidget.appex; sourceTree = BUILT_PRODUCTS_DIR;')
obj(503,'isa = XCConfigurationList; buildConfigurations = ('+uid(512)+','+uid(513)+'); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;')
obj(504,'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ('+uid(510)+','+uid(511)+'); runOnlyForDeploymentPostprocessing = 0;')
obj(505,'isa = PBXBuildFile; fileRef = '+uid(502)+'; settings = { ATTRIBUTES = (RemoveHeadersOnCopy); };')
obj(506,'isa = PBXCopyFilesBuildPhase; buildActionMask = 2147483647; dstPath = ""; dstSubfolderSpec = 13; files = ('+uid(505)+'); name = "Embed App Extensions"; runOnlyForDeploymentPostprocessing = 0;')
obj(507,'isa = PBXTargetDependency; target = '+uid(500)+'; targetProxy = '+uid(508)+';')
obj(508,'isa = PBXContainerItemProxy; containerPortal = '+uid(1)+'; proxyType = 1; remoteGlobalIDString = '+uid(500)+'; remoteInfo = LogbookWidget;')
obj(509,'isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = LogbookWidget.swift; sourceTree = "<group>";')
obj(510,'isa = PBXBuildFile; fileRef = '+uid(509)+';')
shared_index = [f.name for f in files].index('WidgetShared.swift')
obj(511,'isa = PBXBuildFile; fileRef = '+uid(100+shared_index)+';')
for n,name in [(512,'Debug'),(513,'Release')]:
    obj(n,'isa = XCBuildConfiguration; name = '+name+'; buildSettings = { PRODUCT_NAME = "$(TARGET_NAME)"; PRODUCT_BUNDLE_IDENTIFIER = com.adhs.logbook.quietsage.widget; GENERATE_INFOPLIST_FILE = YES; INFOPLIST_FILE = LogbookWidget/Info.plist; CODE_SIGN_ENTITLEMENTS = LogbookWidget/LogbookWidget.entitlements; SWIFT_VERSION = 5.0; "ARCHS[sdk=iphonesimulator*]" = arm64; TARGETED_DEVICE_FAMILY = "1,2"; APPLICATION_EXTENSION_API_ONLY = YES; SKIP_INSTALL = YES; CURRENT_PROJECT_VERSION = 4; MARKETING_VERSION = 0.4.0; };')
obj(520,'isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ('+uid(524)+','+uid(534)+'); runOnlyForDeploymentPostprocessing = 0;')
obj(521,'isa = PBXVariantGroup; children = ('+uid(522)+','+uid(523)+'); name = InfoPlist.strings; sourceTree = "<group>";')
obj(522,'isa = PBXFileReference; lastKnownFileType = text.plist.strings; name = en; path = Logbook/en.lproj/InfoPlist.strings; sourceTree = SOURCE_ROOT;')
obj(523,'isa = PBXFileReference; lastKnownFileType = text.plist.strings; name = de; path = Logbook/de.lproj/InfoPlist.strings; sourceTree = SOURCE_ROOT;')
obj(524,'isa = PBXBuildFile; fileRef = '+uid(521)+';')
obj(526,'isa = PBXFileReference; lastKnownFileType = file; path = LogbookTests/should-android.adhsbak; sourceTree = SOURCE_ROOT;')
obj(527,'isa = PBXBuildFile; fileRef = '+uid(526)+';')
obj(531,'isa = PBXVariantGroup; children = ('+uid(532)+','+uid(533)+'); name = Localizable.strings; sourceTree = "<group>";')
obj(532,'isa = PBXFileReference; lastKnownFileType = text.plist.strings; name = en; path = Logbook/en.lproj/Localizable.strings; sourceTree = SOURCE_ROOT;')
obj(533,'isa = PBXFileReference; lastKnownFileType = text.plist.strings; name = de; path = Logbook/de.lproj/Localizable.strings; sourceTree = SOURCE_ROOT;')
obj(534,'isa = PBXBuildFile; fileRef = '+uid(531)+';')
obj(535,'isa = PBXFileReference; lastKnownFileType = file; path = LogbookTests/could-android.adhsbak; sourceTree = SOURCE_ROOT;')
obj(536,'isa = PBXBuildFile; fileRef = '+uid(535)+';')
Path('iosApp/Logbook.xcodeproj/project.pbxproj').write_text('// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n'+'\n'.join(objects)+'\n}; rootObject = '+uid(1)+'; }\n')
