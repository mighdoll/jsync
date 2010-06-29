package com.digiting.sync


trait Published {
  def publish(publicName:String, root:Syncable) {}
  def publish(publicName:String, generator: ()=>Option[Syncable]) {}
  def unpublish(publicName:String) {}
  def find(publicName:String):Option[Syncable] = None
}

/** uses embedded ids to implement published interface */
class EmbeddedPublished extends Published {
  
}

