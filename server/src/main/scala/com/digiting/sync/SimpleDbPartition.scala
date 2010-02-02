/*   Copyright [2009] Digiting Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.digiting.sync
import org.sublime.amazon.simpleDB.api._
import net.lag.logging.Logger
import com.digiting.util.LogHelper
import com.digiting.sync.aspects.Observable
import scala.collection.mutable
import SyncableSerialize._

//object SimpleDbPartition extends LogHelper {
//  val log = Logger("SimpleDbPartition")
//  
//    /** convert multimap to to map (we never store more than one value) */ 
//  def attributeMap(snapshot:Map[String,Set[String]]):Map[String,String] = {
//    val attributes = mutable.HashMap[String,String]()
//    
//    for {
//      (key, values) <- snapshot
//      value <- values find(_ => true) orElse
//        err("attributeMap() no values found for attribute: %s in %s", 
//              key, snapshot.toString)
//    } {
//      attributes += (key -> value)
//    }
//    
//    Map.empty[String,String] ++ attributes
//  }   
//}
//
//import SimpleDbPartition._
//
//class SimpleDbPartition(admin:SimpleDbAdmin, val partitionRef:PartitionRef, domain:Domain) 
//   extends Partition(partitionRef.partitionName) with LogHelper {
//  
//  val log = Logger("SimpleDbPartition")
//  
//  def put(syncable:Syncable) {
//    log.ifTrace("put: " + syncable)
//    log.ifTrace("  putting: " + 
//      {attributeSets(syncable) map {case (k,v) => k + " = " + v.toString} mkString(", ")})
//    assert (syncable.partition == this)
//    domain item(compositeId(syncable)) update attributeSets(syncable)
//  }
//  
//  def delete(instanceId:String) {
//    deleteByCompositeId(compositeId(instanceId))
//  }
//  
//  private def deleteByCompositeId(compositeId:String) {
//    log.trace("delete: %s", compositeId)
//    domain item(compositeId) clear    
//
//    val membersQuery = String.format("itemName() from %s where memberOf = '%s'", domain.name, compositeId)
//    clearAll(admin.account.select(membersQuery))  
//  }
//
//  /** remove all objects in the partition from the database */
//  def deleteContents() {
//    log.trace("deleteContents")
//    val itemsQuery = String.format("itemName() from %s where itemName() like '%s/%%'", domain.name, partitionRef.partitionName)
//    val itemsStream = admin.account.select(itemsQuery)
//    val itemsList = itemsStream.toList
//    for {item <- itemsList} {
//      deleteByCompositeId(item.name)
//      SyncManager.instanceCache.removeByCompositeId(item.name)
//    }
//    
//    // connected members should already have been deleted but bulk delete to
//    // in case there are any dangling member entries
//    val membersQuery = String.format("itemName() from %s where memberOf like '%s/%%'", domain.name, partitionRef.partitionName)
//    val membersStream = admin.account.select(membersQuery)
//    clearAll(membersStream)
//  }
//  
//  override def deletePartition {
//    super.deletePartition
//    admin.deletePartition(partitionRef)
//  }
//  
//  private def clearAll(stream:Iterable[ItemNameSnapshot]) {
//    for { 
//      snap <-stream 
//      item = domain item(snap.name)
//    } {
//      log.trace("clear: %s", item.name)
//      item clear
//    }
//  }
//
//  def get(instanceId:String):Option[Syncable] = {
//    log.trace("get: %s",instanceId)
//    getRaw(instanceId) map {syncable =>
//      syncable match {
//        case collection:SyncableCollection => loadMembers(collection)
//        case _ => 
//      }
//      syncable
//    }
//  }
//  
//  /** load a single instance */
//  private def getRaw(instanceId:String):Option[Syncable] = {
//    val snapshot = domain item(compositeId(instanceId)) attributes ;
//    log.trace("getRaw: %s found: %s", compositeId(instanceId), snapshot)
//    val loaded = createFromAttributes(instanceId, this, attributeMap(snapshot.self))
//     
//    /** resave the migration back to the database */
//    loaded match {
//      case Some(migration:Migration[_]) => 
//        val migrated = migration.migrate
//        put(migrated)
//        Some(migrated)
//      case _ => loaded
//    }
//  }
//  
//  private def getSingleValue(key:String, map:Map[String,Set[String]]):Option[String] = {
//    for {
//      values <- map get key
//      value <- values find(_ => true) } 
//    yield
//      value
//  }
//
//    
//  def update(change:ChangeDescription) = {
//    log.trace("update(): %s", change)
//    change match {
//      case c:PropertyChange => propertyChange(c)
//      
//      case c:ClearChange => clearChange(c)
//      case c:PutChange => putChange(c)
//      case c:RemoveChange => removeChange(c)
//      
//      case c:InsertAtChange => refreshContents(c)
//      case c:MoveChange => refreshContents(c)
//      case c:RemoveAtChange => refreshContents(c)
//      
//      case c:RemoveMapChange => throw new NotYetImplemented
//      case c:UpdateMapChange => throw new NotYetImplemented
//      
//      case c:BeginWatch => // ignored
//      case c:EndWatch => // ignored
//        
//      case c:CreatedChange[_] => put(c.target.asInstanceOf[Syncable])  // TODO unpickle here
//    }
//  } 
//  
//  private def propertyChange(change:PropertyChange) {
//    val syncable = change.target.asInstanceOf[Syncable]
//    log.trace("propertyChange(): %s %s", compositeId(syncable), change)
//    domain item (compositeId(syncable)) set (change.property -> encodeProperty(change.newValue.value))
//  }
//  
//  private def refreshContents(change:ChangeDescription) {
//    val seq:SyncableSeq[Syncable] = change.target.asInstanceOf[SyncableSeq[Syncable]]
//    clearMembers(seq)		// TODO, add version here:  EVENTUAL ISSUE - these two are not guaranteed to happen in the right order
//    saveMembers(seq)    
//  }
//  
//  private def saveMembers(seq:SyncableSeq[Syncable]) {
//    for {(elem, index) <- seq.zipWithIndex } {
//      saveMember(elem, seq, index)
//    }
//  }
//  
//  private def saveMember(elem:Syncable, collection:SyncableCollection, index:Int) {
//    val member = memberElem(elem, collection, Some(index))
//    log.ifTrace("saveMember: " + {member map {case (k,v) => k + "=" + v} mkString(", ")})
//    domain item (uniqueId()) addSeq member
//  }
//  
//  def repairCollectionById(id:String) {
//    getRaw(id) match {
//      case Some(collection:SyncableCollection) => 
//        val memberSnaps = loadMemberSnapshots(collection)
//        repairCollection(collection, memberSnaps)
//      case x =>
//        log.error("id: %s is not a collection: %s", id, x)
//    }
//  }
//  
//  private def repairCollection(collection:SyncableCollection, memberSnaps:List[ItemNameSnapshot]) {
//    log.warning("repairing collection: %s", collection)
//    val validMembers = 
//      for {
//        snap <- memberSnaps
//        elemValues <- snap.get("elem") 
//        elemRefString <- elemValues find {_ => true} 
//        elemRef <- ReferenceString.unapply(elemRefString) 
//        elem <- getFromRef(elemRef)       
//      } yield {
//        val index:Int = getIndex(snap) getOrElse 0 
//        (elem, index)
//      }
//    
//    clearMembers(collection)
//    
//    val sorted = sortMembers(validMembers)
//    var index = -1;
//    val reIndexed = 
//      for {
//        (elem, ignoreOrigDex) <- sorted 
//      } yield {
//        index += 1
//        saveMember(elem, collection, index)
//        (elem, index)
//      }
//    
//    addLoadedMembers(reIndexed, collection)
//  }
//  
//  private def loadMembers(collection:SyncableCollection) {
//    val memberSnaps = loadMemberSnapshots(collection)
//    try {
//      val members = loadFromSnapshots(memberSnaps)
//      addLoadedMembers(members, collection)
//    } catch {
//      case ex:InconsistentCollection => 
//        repairCollection(collection, memberSnaps)
//    }    
//  }
//  
//  private def sortMembers(members:Iterable[(Syncable, Int)]):Iterable[(Syncable, Int)] = {
//    members.toList.sort {(a, b) =>	// how to make this clearer in SCALA
//      val Pair(_:Syncable, aDex:Int) = a
//      val Pair(_:Syncable, bDex:Int) = b
//      aDex < bDex
//    }
//  }
//    
//  private def addLoadedMembers(members:Iterable[(Syncable, Int)], collection:SyncableCollection) {
//    collection match {
//      case s:SyncableSet[_] =>         
//        val set = s.asInstanceOf[SyncableSet[Syncable]]
//        members foreach {case (elem, index_ignored) => set += elem}
//      case s:SyncableSeq[_] => 
//        val seq = s.asInstanceOf[SyncableSeq[Syncable]]
//        val sorted = sortMembers(members)
//        sorted foreach {case (elem, index) => 
//          if (seq.length != index) {
//            log.error("loadMembers: indices are not correct: %s %s", seq.length, index)
//          }
//          seq += elem 
//        }
//      case _ => 
//        log.error("loadMembers() collection type %s not yet implemented", collection.getClass.getName)
//        throw new NotYetImplemented
//    }
//  }
//  
//  private def loadFromSnapshots(snapshots:List[ItemNameSnapshot]):Iterable[(Syncable, Int)] = {
//    for {
//      snap <- snapshots
//      elemValues <- snap.get("elem") orElse {
//        err("loadFromSnapshots can't find attribute 'elem' in: %s ", snap.toString)        
//        throw new InconsistentCollection
//      }
//      elemRefString <- elemValues find {_ => true} orElse {
//        err("loadFromSnapshots found no values for attribute 'elem'")
//        throw new InconsistentCollection
//      }
//      elemRef <- ReferenceString.unapply(elemRefString) orElse {    // We use ReferenceString rather than compositeId to someday support lazy loading
//        err("loadFromSnapshots can't parse reference: %s", elemRefString)        
//        throw new InconsistentCollection
//      }
//      member <- getFromRef(elemRef) orElse {				// Consider CONSISTENCY here, if the referenced element is missing, should we clean it up?
//        err("loadFromSnapshots can't find referenced object %s", elemRef.toString)
//        throw new InconsistentCollection
//      }
//    } yield {
//      val index:Int = getIndex(snap) getOrElse 0 
//      log.trace("loadFromSnapshots, loaded item: %s from %s @%d ", member, attributesToString(snap), index)
//      (member, index)
//    }
//  }
//  
//  private def getIndex(snap:ItemNameSnapshot):Option[Int] = {
//    for {
//      indexValues <- snap.get("index") 
//      indexString <- indexValues find {_ => true} orElse
//        err("getIndex found no attributes for 'index' in %s", snap.toString)
//    } yield
//      Integer.valueOf(indexString).intValue
//  }
//  
//  private def loadMemberSnapshots(collection:SyncableCollection):List[ItemNameSnapshot] = {
//    val query = String.format("* from %s where memberOf = '%s'", domain.name, ReferenceString(collection))
//    log.trace("loadMemberSnapshots query: %s", query)
//    val stream = admin.account.select(query)
//    stream.toList
//  }  
//
//  // spooky converter.  Tired of casting.  Note: We should probably get rid of observable and use syncable everywhere.
//  implicit def observableToSyncable(observable:Observable):Syncable = observable.asInstanceOf[Syncable]
//  
//  private def uniqueId():String = {
//    RandomIds.randomUriString(32)
//  }
//  
//  private def putChange(change:PutChange) {
//    val member = memberElem(change.newValue.asInstanceOf[Syncable], 
//      change.target.asInstanceOf[SyncableCollection], None)
//    val memberId = uniqueId()
//    log.ifTrace("putChange() %s %s", memberId, member map {case (k,v) => k + "->" + v} mkString ", ")
//    domain item (memberId) addSeq member
//  }
//  
//  /** return attributes for a collection membership element */
//  private def memberElem(elem:Syncable, collection:SyncableCollection, 
//    index:Option[Int]): Seq[(String,String)] = {
//	    val base = 
//       List[(String,String)]("memberOf" -> ReferenceString(collection), 
//	       "elem" -> ReferenceString(elem))
// 
//    index match {
//      case Some(dex) => ("index" -> dex.toString) :: base 
//      case _ => base
//    }
//  }
//
//  private def clearChange(change:ClearChange) {
//    clearMembers(change.target.asInstanceOf[SyncableCollection])
//  }
//  
//  private def clearMembers(collection:SyncableCollection) {
//    for {
//      memberSnap <- loadMemberSnapshots(collection)	// don't really need a * query here, slight inefficiency
//      item = domain item(memberSnap.name)
//    } {
//      log.trace("clearMembers, clearing item: %s", attributesToString(memberSnap))
//      item clear
//    }       
//  }
//  
//  private def attributesToString(memberSnap:ItemNameSnapshot):String = {
//    memberSnap map { 
//      case (key, valueSet) => 
//        val value = valueSet mkString("&")
//        key + "=" + value
//    } mkString(", ")
//  }
//  
//  private def removeChange(change:RemoveChange) {
//    change.target.target foreach {target =>
//      val query = String.format("itemName() from %s where memberOf = '%s' and elem = '%s'", 
//        domain.name, ReferenceString(target), ReferenceString(change.oldVal.asInstanceOf[Syncable]))
//      log.trace("removeChange query: %s", query)
//      for {
//        snapshot <- admin.account.select(query)
//      } {
//        log.trace("removeChange removing: %s", snapshot.name)
//        val item = domain item(snapshot.name)	
//        item clear
//      }
//    }
//  }
//   
//  
//  private def getFromRef(ref:Reference):Option[Syncable] = {
//    SyncManager.get(ref.partition,ref.id) orElse {
//      log.error("getFromRef: can't find ref: %s" , ref)
//      None
//    }
//  }
//      
//  private def compositeId(syncable:Syncable):String = {
//    compositeId(syncable.id)
//  }
//  
//  private def compositeId(instanceId:String):String = {
//    partitionRef.partitionName + "/" + instanceId
//  }  
//  
//  def attributeSets(syncable:Syncable):Map[String, (Set[String], Boolean)] = {    
//    val elems = syncableAttributes(syncable)
//      
//    // create map for simpledb api, with one and only one attribute value per property
//    val elemSets =
//      for {(prop, valueString) <- elems}    
//        yield (prop, (Set[String](valueString), true))		        
//    Map() ++ elemSets     
//  }
//  
//  
//  override def debugPrint() {
//    log.info("partition %s:", partitionRef.compositeId)
//    val query = String.format("* from %s", domain.name)
//    for {
//      snapshot <- admin.account.select(query)      
//    } {
//      log.info("  %s: %s", snapshot.name, attributesToString(snapshot))
//    }
//  }
//  
//  class InconsistentCollection extends Exception
//}
//
///**
// * @param domain -- domain holds a group of partitions e.g. beta, production-1, etc.
// */
//case class PartitionRef(val domain:String, val partitionName:String) {
//  lazy val compositeId = domain + "." + partitionName
//  override def toString() = compositeId
//}
