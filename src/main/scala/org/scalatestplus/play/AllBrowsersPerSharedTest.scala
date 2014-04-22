/*
 * Copyright 2001-2014 Artima, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.scalatestplus.play

import play.api.test._
import org.scalatest._
import org.scalatest.events._
import org.scalatest.tags._
import selenium.WebBrowser
import concurrent.Eventually
import concurrent.IntegrationPatience
import org.openqa.selenium.WebDriver
import BrowserFactory.{NoDriver, WithoutDriver}
import org.openqa.selenium.firefox.FirefoxProfile
import org.openqa.selenium.safari.SafariDriver
import org.openqa.selenium.chrome.ChromeDriver

/**
 * Trait that uses a [[http://doc.scalatest.org/2.1.3/index.html#org.scalatest.FlatSpec@sharedTests ''shared test'']] approach to enable you to run the same tests on multiple browsers in a ScalaTest `Suite` with minimal boilerplate.
 *
 * This trait overrides `Suite`'s `withFixture` and `runTest` lifecycle methods to create a new `WebDriver`, `TestServer`, and
 * `FakeApplication` instance before executing each test, and overrides the `tags` lifecycle method to tag the shared tests so you can
 * filter them by browser type.
 *
 * You'll need to place any tests that you want executed by multiple browsers in a `registerSharedTests` method. Because all tests in a ScalaTest `Suite`
 * must have unique names, you'll need to append the browser name (available from the `BrowserInfo` passed
 * to `registerSharedTests`) to each test name:
 * 
 * <pre class="stHighlight">
 * def registerSharedTests(browser: BrowserInfo) {
 *   "The blog app home page" must {
 *     "have the correct title " + browser.name in {
 *        go to (host + "index.html")
 *        pageTitle should be ("Awesome Blog")
 *     } 
 * </pre>
 * 
 * All tests registered via `registerSharedTests` will be registered for each possible `WebDriver`. When
 * running, any tests for browser drivers that are unavailable
 * on the current platform will be canceled.
 * All tests registered under `registerSharedTests` will be
 * tagged automatically if they end with a browser name in square brackets. For example, if a test name ends
 * with `[Firefox]`, it will be automatically tagged with `"org.scalatest.tags.FirefoxBrowser"`. This will
 * allow you can include or exclude the shared tests by browser type using ScalaTest's regular tagging feature.
 *
 * Use tagging to include or exclude browsers that you sometimes want to test with, but not always. If you
 * ''never'' want to test with a particular browser, you can prevent tests for it from being registered at all
 * by overriding `browsers` and excluding its `BrowserInfo` in the returned `Seq`. For example, to disable registration of
 * tests for `HtmlUnit`, you'd write:
 *
 * <pre class="stHighlight">
 * override val browsers: IndexedSeq[BrowserInfo] =
 *   Vector(
 *     FirefoxInfo,
 *     SafariInfo,
 *     InternetExplorerInfo,
 *     ChromeInfo
 *   )
 * </pre>
 */
trait AllBrowsersPerSharedTest extends SuiteMixin with WebBrowser with Eventually with IntegrationPatience { this: Suite =>

  /**
   * Method to provide `FirefoxProfile` for creating `FirefoxDriver`, you can override this method to
   * provide a customized instance of `FirefoxProfile`
   *
   * @return an instance of `FirefoxProfile`
   */
  protected lazy val firefoxProfile: FirefoxProfile = new FirefoxProfile

  // Not sealed on purpose, so people can extend it if other
  // Browser driver types appear (or we could just use strings
  // for the browser names)
  /**
   * Abstract super class for browser information used to register tests shared by multiple browser drivers.
   *
   * @param name the browser name, surrounded by square brackets
   * @param tagName the browser tag name
   */
  abstract class BrowserInfo(val name: String, val tagName: String) {
    /**
     * Creates a `WebDriver` instance for the represented browser.
     *
     * @return `WebDriver` instance for the represented browser
     */
    def createWebDriver: WebDriver
  }

  /**
   * Case object for Firefox browser info.
   */
  case object FirefoxInfo extends BrowserInfo("[Firefox]", "org.scalatest.tags.FirefoxBrowser") {
    /**
     * Creates a `WebDriver` instance for Firefox.
     *
     * @return a Firefox `WebDriver` instance
     */
    def createWebDriver: WebDriver = WebDriverFactory.createFirefoxDriver(firefoxProfile)
  }

  /**
   * Case object for Safari browser info.
   */
  case object SafariInfo extends BrowserInfo("[Safari]", "org.scalatest.tags.SafariBrowser") {
    /**
     * Creates a `WebDriver` instance for Safari.
     *
     * @return a Safari `WebDriver` instance
     */
    def createWebDriver: WebDriver = WebDriverFactory.createSafariDriver
  }

  /**
   * Case object for Internet Explorer browser info.
   */
  case object InternetExplorerInfo extends BrowserInfo("[InternetExplorer]", "org.scalatest.tags.InternetExplorerBrowser") {
    /**
     * Creates a `WebDriver` instance for Internet Explorer.
     *
     * @return an Internet Explorer `WebDriver` instance
     */
    def createWebDriver: WebDriver = WebDriverFactory.createInternetExplorerDriver
  }

  /**
   * Case object for Chrome browser info.
   */
  case object ChromeInfo extends BrowserInfo("[Chrome]", "org.scalatest.tags.ChromeBrowser") {
    /**
     * Creates a `WebDriver` instance for Chrome .
     *
     * @return a Chrome `WebDriver` instance
     */
    def createWebDriver: WebDriver = WebDriverFactory.createChromeDriver
  }

  /**
   * Case object for `HtmlUnit` browser info.
   */
  case object HtmlUnitInfo extends BrowserInfo("[HtmlUnit]", "org.scalatest.tags.HtmlUnitBrowser") {
    /**
     * Creates an `HtmlUnit` `WebDriver` instance.
     *
     * @return an `HtmlUnit` `WebDriver` instance
     */
    def createWebDriver: WebDriver = WebDriverFactory.createHtmlUnitDriver
  }

  /**
   * Info for available browsers. Override to add in custom `BrowserInfo` implementations.
   */
  protected val browsers: IndexedSeq[BrowserInfo] =
    Vector(
      FirefoxInfo,
      SafariInfo,
      InternetExplorerInfo,
      ChromeInfo,
      HtmlUnitInfo
    )

  private var privateApp: FakeApplication = _

  /**
   * Implicit method that returns the <code>FakeApplication</code> instance for the current test.
   */
  implicit def app: FakeApplication = synchronized { privateApp }

  /**
   * The port used by the `TestServer`.  By default this will be set to the result returned from
   * `Helpers.testServerPort`. You can override this to provide a different port number.
   */
  val port: Int = Helpers.testServerPort

  /**
   * Implicit <code>PortNumber</code> instance that wraps <code>port</code>. The value returned from <code>portNumber.value</code>
   * will be same as the value of <code>port</code>.
   */
  implicit lazy val portNumber: PortNumber = PortNumber(port)

  private var privateWebDriver: WebDriver = _

  private var privateWebDriverName: String = _

  /**
   * Implicit method to get the `WebDriver` for the current test.
   */
  implicit def webDriver: WebDriver = synchronized { privateWebDriver }

  /**
   * Registers tests "shared" by multiple browsers.
   *
   * Implement this method by placing tests you wish to run for multiple browsers. This method
   * will be called during the initialization of this trait once for each browser whos
   *
   * @param browser the passed in `BrowserInfo` instance, you must append `browser.name` to all tests register here.
   */
  def registerSharedTests(browser: BrowserInfo): Unit

  for (browser <- browsers) {
    registerSharedTests(browser)
  }

  private def mergeMap[A, B](ms: List[Map[A, B]])(f: (B, B) => B): Map[A, B] =
    (Map[A, B]() /: (for (m <- ms; kv <- m) yield kv)) { (a, kv) =>
      a + (if (a.contains(kv._1)) kv._1 -> f(a(kv._1), kv._2) else kv)
    }

  /**
   * Automatically tag browser tests with browser tags based on the test name: if a test ends in a browser
   * name in square brackets, it will be tagged as using that browser. The browser tags will be merged with
   * tags returned from `super.tags`, so no existing tags will be lost when the browser tags are added.
   *
   * @return `super.tags` with additional browser tags added for any browser-specific tests 
   */
  abstract override def tags: Map[String, Set[String]] = {
    val generatedBrowserTags: Map[String, Set[String]] = Map.empty ++ testNames.map { tn =>
      browsers.find(b => tn.endsWith(b.name)) match {
        case Some(b) => (tn, Set(b.tagName))
        case None => (tn, Set.empty[String])
      }
    }
    mergeMap(List(super.tags, generatedBrowserTags.filter(!_._2.isEmpty))) { case (s1, s2) =>
      s1 ++ s2  // just add the 2 sets together
    }
  }

  /**
   * Checks the result of the `webDriver` method before running each test, canceling the
   * test if it is a `NoDriver` (which means the driver was not available on the current platform).
   * Otherwise, creates a new instance of `TestServer` for the test and ensures it is cleaned up
   * after the test completes.
   *
   * @param test the no-arg test function to run with a fixture
   * @return the `Outcome` of the test execution
   */
  abstract override def withFixture(test: NoArgTest): Outcome =
    webDriver match {
      case NoDriver(ex) =>
        val name = test.configMap.getRequired[String]("org.scalatestplus.play.webDriverName")
        val message = Resources("cantCreateDriver", name.trim)
        ex match {
          case Some(e) => Canceled(message, e)
          case None => Canceled(message)
        }
      case _ =>
        Helpers.running(TestServer(port, app)) {
          super.withFixture(test)
        }
    }

  /**
   * Creates a `WebDriver` and `FakeApplication` and adds entries
   * to the config map for the app, port number, web driver, and the web driver's name before
   * executing the specified test. After the test
   * completes, ensures the `WebDriver` instance is closed.
   *
   * @param testName the name of one test to run.
   * @param args the `Args` for this run
   * @return a `Status` object that indicates when the test started by this method has completed, and whether or not it failed .
   */
  abstract override def runTest(testName: String, args: Args): Status = {
    // looks at the end of the test name, and if it is one of the blessed ones,
    // sets the port, driver, etc., before, and cleans up after, calling super.runTest
    synchronized {
      privateApp = new FakeApplication()
      val (theWebDriver, theWebDriverName) =
        browsers.find(b => testName.endsWith(b.name)) match {
          case Some(b) => (b.createWebDriver, b.name)
          case None => (WithoutDriver, "WithoutDriver")
        }
      privateWebDriver = theWebDriver
      privateWebDriverName = theWebDriverName
    }
    try {
      val newConfigMap = args.configMap +
        ("org.scalatestplus.play.app" -> app) +
        ("org.scalatestplus.play.port" -> port) +
        ("org.scalatestplus.play.webDriver" -> webDriver) +
        ("org.scalatestplus.play.webDriverName" -> privateWebDriverName)
      val newArgs = args.copy(configMap = newConfigMap)
      super.runTest(testName, newArgs)
    }
    finally {
      webDriver match {
        case NoDriver(_) => // do nothing
        case WithoutDriver => // do nothing
        case safariDriver: SafariDriver => safariDriver.quit()
        case chromeDriver: ChromeDriver => chromeDriver.quit()
        case theDriver => theDriver.close()
      }
    }
  }
}
