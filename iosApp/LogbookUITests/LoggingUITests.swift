import XCTest

final class LoggingUITests: XCTestCase {
    func testLoggingUndoAndGermanLargeText() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        if app.buttons["Get started"].waitForExistence(timeout: 5) {
            app.buttons["Get started"].tap()
            let dose = app.textFields["Your usual dose (mg)"]
            XCTAssertTrue(dose.waitForExistence(timeout: 5));dose.tap();dose.typeText("10")
            app.buttons["Save"].tap()
        }
        let quick = app.buttons["Log now · 10 mg"]
        XCTAssertTrue(quick.waitForExistence(timeout: 10));quick.tap()
        let undo = app.buttons["Dose logged · Undo"]
        XCTAssertTrue(undo.waitForExistence(timeout: 5));undo.tap()
        app.buttons["Log dose"].tap()
        XCTAssertTrue(app.textFields["Dose (mg)"].waitForExistence(timeout: 5))
        app.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Last logged"].waitForExistence(timeout: 5))
        app.buttons["Dose logged · Undo"].tap()
        app.terminate()
        app.launchArguments = ["-AppleLanguages", "(de)", "-AppleLocale", "de_DE", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        XCTAssertTrue(app.buttons["Jetzt erfassen · 10 mg"].waitForExistence(timeout: 10))
        let home = XCTAttachment(screenshot: app.screenshot());home.name = "German large text home";home.lifetime = .keepAlways;add(home)
        app.buttons["Dosis erfassen"].tap()
        XCTAssertTrue(app.textFields["Dosis (mg)"].waitForExistence(timeout: 5))
        let form = XCTAttachment(screenshot: app.screenshot());form.name = "German large text form";form.lifetime = .keepAlways;add(form)
        app.buttons["Speichern"].tap()
        XCTAssertTrue(app.staticTexts["Zuletzt erfasst"].waitForExistence(timeout: 5))
    }
    func testObservationWithoutDoseAndSummaryOptions() {
        continueAfterFailure = false
        let app = XCUIApplication();app.launchArguments = ["-AppleLanguages","(en)","-AppleLocale","en_US"];app.launch()
        if app.buttons["Get started"].waitForExistence(timeout: 3) {
            app.buttons["Get started"].tap();let dose = app.textFields["Your usual dose (mg)"];XCTAssertTrue(dose.waitForExistence(timeout: 5));dose.tap();dose.typeText("10");app.buttons["Save"].tap()
        }
        app.tabBars.buttons["History"].tap()
        app.buttons["Observations & non-use"].tap()
        app.buttons["Add observation"].tap()
        app.buttons["Response, Choose a response"].tap();app.buttons["None"].tap()
        app.buttons["Save"].tap()
        XCTAssertTrue(app.buttons["Add observation"].waitForExistence(timeout: 5))
        let image = XCTAttachment(screenshot: app.screenshot());image.name = "Independent observation";image.lifetime = .keepAlways;add(image)
        app.tabBars.buttons["Export"].tap()
        XCTAssertTrue(app.switches["Summary with details"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.switches["Include free-text notes"].exists)
    }

    func testOptionalMeasurementAndWeeklyOverview() {
        continueAfterFailure = false
        let app = XCUIApplication();app.launchArguments = ["-AppleLanguages","(en)","-AppleLocale","en_US","-UIPreferredContentSizeCategoryName","UICTContentSizeCategoryL"];app.launch()
        if app.buttons["Get started"].waitForExistence(timeout: 3) {
            app.buttons["Get started"].tap();let dose = app.textFields["Your usual dose (mg)"];XCTAssertTrue(dose.waitForExistence(timeout: 5));dose.tap();dose.typeText("10");app.buttons["Save"].tap()
        }
        app.tabBars.buttons["Settings"].tap()
        for label in ["Measurements","Weekly overview"] {
            let toggle = app.switches[label]
            for _ in 0..<6 { if toggle.isHittable { break };app.swipeUp() }
            XCTAssertTrue(toggle.isHittable)
            if toggle.value as? String == "0" { toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93,dy: 0.5)).tap() }
            XCTAssertEqual(toggle.value as? String,"1")
        }
        app.tabBars.buttons["History"].tap();app.buttons["Observations & non-use"].tap();app.buttons["Add measurement"].tap()
        let upper = app.textFields["Systolic (mmHg)"],lower = app.textFields["Diastolic (mmHg)"]
        XCTAssertTrue(upper.waitForExistence(timeout: 5));upper.tap();upper.typeText("120");lower.tap();lower.typeText("80");app.buttons["Save"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@","120 / 80 mmHg")).firstMatch.waitForExistence(timeout: 5))
        app.navigationBars.buttons.element(boundBy: 0).tap();app.buttons["Weekly overview"].tap()
        XCTAssertTrue(app.buttons["Previous week"].waitForExistence(timeout: 5))
        let image = XCTAttachment(screenshot: app.screenshot());image.name = "Could weekly overview";image.lifetime = .keepAlways;add(image)
    }

}
