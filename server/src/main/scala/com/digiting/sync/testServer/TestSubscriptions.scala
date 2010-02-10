package com.digiting.sync.testServer

/**
 * Setup a special partition that supports client tests.  The test partition includes
 * some published objects that the client can fetch to verify fetching from the server.  Some 
 * of the published objects respond to client changes to enable round trip testing.
 */
object TestSubscriptions {
//  val testPartition = new RamPartition("test")
  
  def init() {}
  
//  SyncManager.withPartition(testPartition) {
//    
//  }
}
