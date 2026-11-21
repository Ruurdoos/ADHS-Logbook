import XCTest

final class LoggingUITests: XCTestCase {
    private let fixtureID = UUID().uuidString
    override func setUp() { super.setUp();continueAfterFailure = false }
    func testLoggingUndoAndGermanLargeText() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchArguments += ["--ui-test-data",fixtureID];app.launch()
        if app.buttons["Get started"].waitForExistence(timeout: 5) {
            app.buttons["Get started"].tap()
            let dose = app.textFields["Your usual dose (mg)"]
            XCTAssertTrue(dose.waitForExistence(timeout: 5));dose.tap();dose.typeText("10")
            app.buttons["Start"].tap()
        }
        let quick = app.buttons["Taken · 10 mg"]
        XCTAssertTrue(quick.waitForExistence(timeout: 10));quick.tap()
        XCTAssertTrue(app.staticTexts["Dose logged · 10 mg"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@","Methylphenidate IR · 10 mg")).firstMatch.exists)
        let undo = app.buttons["Undo"]
        XCTAssertTrue(undo.waitForExistence(timeout: 5));undo.tap()
        XCTAssertTrue(app.staticTexts["Nothing recorded today"].waitForExistence(timeout: 5))
        app.buttons["＋ Record"].tap()
        XCTAssertTrue(app.buttons["Focus"].waitForExistence(timeout: 5))
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@","Medication,")).firstMatch.tap()
        XCTAssertTrue(app.textFields["Dose (mg)"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["How are you feeling?"].exists)
        app.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Last logged"].waitForExistence(timeout: 5))
        app.buttons["Undo"].tap()
        app.terminate()
        app.launchArguments = ["-AppleLanguages", "(de)", "-AppleLocale", "de_DE", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launchArguments += ["--ui-test-data",fixtureID];app.launch()
        XCTAssertTrue(app.buttons["Eingenommen · 10 mg"].waitForExistence(timeout: 10))
        let home = XCTAttachment(screenshot: app.screenshot());home.name = "German large text home";home.lifetime = .keepAlways;add(home)
        let record = app.buttons["＋ Erfassen"]
        for _ in 0..<8 { if record.isHittable { break };app.swipeUp() }
        record.tap()
        let medication = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@","Medikament,")).firstMatch
        XCTAssertTrue(medication.waitForExistence(timeout: 5));medication.tap()
        XCTAssertTrue(app.textFields["Dosis (mg)"].waitForExistence(timeout: 5))
        let form = XCTAttachment(screenshot: app.screenshot());form.name = "German large text form";form.lifetime = .keepAlways;add(form)
        app.buttons["Speichern"].tap()
        XCTAssertTrue(app.staticTexts["Zuletzt erfasst"].waitForExistence(timeout: 5))
    }
    func testObservationWithoutDoseAndSummaryOptions() {
        continueAfterFailure = false
        let app = XCUIApplication();app.launchArguments = ["-AppleLanguages","(en)","-AppleLocale","en_US"];app.launchArguments += ["--ui-test-data",fixtureID];app.launch()
        if app.buttons["Get started"].waitForExistence(timeout: 3) {
            app.buttons["Get started"].tap();let dose = app.textFields["Your usual dose (mg)"];XCTAssertTrue(dose.waitForExistence(timeout: 5));dose.tap();dose.typeText("10");app.buttons["Start"].tap()
        }
        XCTAssertTrue(app.buttons["＋ Record"].waitForExistence(timeout: 10))
        app.buttons["＋ Record"].tap()
        XCTAssertTrue(app.buttons["Focus"].waitForExistence(timeout: 5));app.buttons["Focus"].tap()
        XCTAssertTrue(app.buttons["Response, Choose a response"].waitForExistence(timeout: 5))
        app.buttons["Response, Choose a response"].tap();app.buttons["None"].tap()
        app.buttons["Save"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@","Focus · None")).firstMatch.waitForExistence(timeout: 5))
        app.buttons["Not taken"].tap()
        XCTAssertTrue(app.navigationBars["Record not taken"].waitForExistence(timeout: 5))
        app.buttons["Save"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@","No doses taken during this period · Methylphenidate IR")).firstMatch.waitForExistence(timeout: 5))
        let image = XCTAttachment(screenshot: app.screenshot());image.name = "Independent observation";image.lifetime = .keepAlways;add(image)
        app.tabBars.buttons["Export"].tap()
        XCTAssertTrue(app.switches["Summary with details"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.switches["Include free-text notes"].exists)
    }

    func testOptionalMeasurementAndWeeklyOverview() {
        continueAfterFailure = false
        let app = XCUIApplication();app.launchArguments = ["-AppleLanguages","(en)","-AppleLocale","en_US","-UIPreferredContentSizeCategoryName","UICTContentSizeCategoryL"];app.launchArguments += ["--ui-test-data",fixtureID];app.launch()
        if app.buttons["Get started"].waitForExistence(timeout: 3) {
            app.buttons["Get started"].tap();let dose = app.textFields["Your usual dose (mg)"];XCTAssertTrue(dose.waitForExistence(timeout: 5));dose.tap();dose.typeText("10");app.buttons["Start"].tap()
        }
        app.tabBars.buttons["Settings"].tap()
        for label in ["Measurements","Weekly overview"] {
            let toggle = app.switches[label]
            for _ in 0..<6 { if toggle.isHittable { break };app.swipeUp() }
            XCTAssertTrue(toggle.isHittable)
            if toggle.value as? String == "0" { toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93,dy: 0.5)).tap() }
            XCTAssertEqual(toggle.value as? String,"1")
        }
        app.tabBars.buttons["Home"].tap();app.buttons["＋ Record"].tap()
        let measurement = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@","Measurement,")).firstMatch
        XCTAssertTrue(measurement.waitForExistence(timeout: 5));measurement.tap()
        let upper = app.textFields["Systolic (mmHg)"],lower = app.textFields["Diastolic (mmHg)"]
        XCTAssertTrue(upper.waitForExistence(timeout: 5));upper.tap();upper.typeText("120");lower.tap();lower.typeText("80");app.buttons["Save"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@","120 / 80 mmHg")).firstMatch.waitForExistence(timeout: 5))
        app.tabBars.buttons["History"].tap();app.buttons["Weekly overview"].tap()
        XCTAssertTrue(app.buttons["Previous week"].waitForExistence(timeout: 5))
        let image = XCTAttachment(screenshot: app.screenshot());image.name = "Could weekly overview";image.lifetime = .keepAlways;add(image)
    }

    func testMedicationFreeOnboardingAndSleepQuality() {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages","(en)","-AppleLocale","en_US","--ui-test-data",UUID().uuidString]
        app.launch()
        XCTAssertFalse(app.buttons["Start without medication"].exists)
        XCTAssertTrue(app.buttons["Get started"].waitForExistence(timeout: 10))
        app.buttons["Get started"].tap()
        XCTAssertTrue(app.buttons["Medication, Methylphenidate IR"].waitForExistence(timeout: 5))
        app.buttons["Medication, Methylphenidate IR"].tap();app.buttons["No medication"].tap()
        app.buttons["Start"].tap()
        XCTAssertTrue(app.buttons["＋ Record"].waitForExistence(timeout: 10))
        app.buttons["＋ Record"].tap()
        XCTAssertTrue(app.buttons["Sleep"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@","Not taken")).firstMatch.exists)
        app.buttons["Sleep"].tap()
        XCTAssertTrue(app.buttons["Category, Sleep"].waitForExistence(timeout: 5))
        app.buttons["Response, Choose a response"].tap();app.buttons["Rating"].tap()
        XCTAssertTrue(app.staticTexts["Sleep quality: 0 = very poor · 4 = very good"].exists)
        app.buttons["Rating, Choose a response"].tap();app.buttons["4: Very good"].tap()
        app.buttons["Save"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@","Sleep · 4 / 4")).firstMatch.waitForExistence(timeout: 10))
        app.tabBars.buttons["History"].tap();app.buttons["Observations & non-use"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@","Sleep")).firstMatch.waitForExistence(timeout: 5))
    }
}
