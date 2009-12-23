/*
Copyright 2009 Jon-Anders Teigen

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.jteigen.scalatest

import org.junit.runner.notification.{RunNotifier, Failure}
import org.junit.runner.{Runner, RunWith, Description}
import org.scalatest.{Suite, Report, Reporter, TestFailedException, Stopper}

class JUnit4Runner(clazz: Class[Suite]) extends Runner {
  lazy val suite = clazz.newInstance

  lazy val description = descriptionFor(suite)

  def run(notifier: RunNotifier) {
    suite.execute(
      None,
      new JUnit4Reporter(notifier),
      new Stopper {},
      Set.empty,
      Set("org.scalatest.Ignore"),
      Map.empty,
      None)
  }

  def getDescription = description

  def descriptionFor(suite: Suite): Description = {
    val desc = Description.createSuiteDescription(suite.suiteName)
    suite.testNames.foreach(name => desc.addChild(Description.createTestDescription(clazz, name)))
    suite.nestedSuites.foreach(nested => desc.addChild(descriptionFor(nested)))
    desc
  }

  class JUnit4Reporter(notifier: RunNotifier) extends Reporter {
    override def testStarting(report: Report) {
      notifier.fireTestStarted(Description.createTestDescription(clazz, report.name))
    }

    override def testSucceeded(report: Report) {
      notifier.fireTestFinished(Description.createTestDescription(clazz, report.name))

    }

    override def testFailed(report: Report) {
      val desc = Description.createTestDescription(clazz, report.name)
      notifier.fireTestFailure(new Failure(desc, report.throwable.get))
      notifier.fireTestFinished(desc)
    }

    override def testIgnored(report: Report) {
      val desc = Description.createTestDescription(clazz, report.name)
      notifier.fireTestIgnored(desc)
      notifier.fireTestFinished(desc)
    }
  }
}