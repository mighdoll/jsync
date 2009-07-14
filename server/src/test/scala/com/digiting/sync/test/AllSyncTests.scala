package com.digiting.sync.test
import org.scalatest.SuperSuite
import com.jteigen.scalatest.JUnit4Runner
import org.junit.runner.RunWith

@RunWith(classOf[JUnit4Runner])
class AllSyncTests extends SuperSuite (
  List(new ObservationTest, 
       new SyncableTest,
       new SyncableAccessorTest, 
  	   new MultiMapTest,
  	   new ParseTest,
  	   new ProtocolTest)
  ) 

