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
import collection.mutable

/**
 * Administration for partitions 
 */
class SimpleDbAdmin(credentials:SimpleDbAccount) {
  val log = Logger("SimpleDbAdmin")
  val account = new SimpleDBAccount(credentials.accessKeyId, credentials.secretAccessKey)
  
    // set of domains in simpledb, itemName is domain
  val domains:mutable.Map[String, Domain] = {	
    val pairs = account.domains.map {domain => (domain.name, domain)}  
    mutable.Map() ++ pairs
  }
  
    // set of partitions in simpledb, itemName is domain.partition
  val partitionsDomain = {
    val name = "_partitions"
    domains get name getOrElse {
      // shouldn't be needed anymore, unless we get a new amazon account
	  val domain:Domain = account.domain(name) 
      domain.create
      domains += (name -> domain)
      domain
      log.fatal("_partitions domain not found, new one created")
      throw new ImplementationError
    }
  }
  
  def getPartition(domainName:String, partitionName:String):Option[SimpleDbPartition] = {
    val ref = PartitionRef(domainName, partitionName)
    val partitionEntry = partitionsDomain item(ref.compositeId) attributes
    
    verifyAttribute(partitionEntry, "partition", partitionName) flatMap {_ =>
    verifyAttribute(partitionEntry, "domain", domainName) } flatMap {_ =>
      log.trace("getPartition() found: %s/%s", domainName, partitionName)
      val partition = new SimpleDbPartition(this, ref, getDomain(domainName))
      Some(partition)
    } orElse {
      log.trace("getPartition() not found: %s/%s", domainName, partitionName)
      None
    }
  }
  
  def verifyAttribute(attributes:ItemSnapshot, name:String, value:String):Option[Boolean] = {
    for {
      values <- attributes.get(name)
      found <- values.find {_=>true} 
    } yield {
      found == value
    }
  }
            
  def partition(partitionRef:PartitionRef):SimpleDbPartition = 
    Partitions.get(partitionRef.partitionName) match {
      case Some(simple:SimpleDbPartition) => simple
      case Some(other) => log.error("not a simpleDb partition type: %s", other);  throw new ImplementationError
      case None => createPartition(partitionRef)                                                                                  
    }

  private def getDomain(domain:String):Domain = {
    domains get domain getOrElse 
      createDomain(domain) 
  }
  
  private def createPartition(partitionRef:PartitionRef):SimpleDbPartition = {
    val domain = getDomain(partitionRef.domain)
    
    // create bookeepking entry in the _partitions domain, overwriting it it exists
    // we don't cache the partitions in RAM (the Partitions class does)
    partitionsDomain item(partitionRef.compositeId) set 
      ("domain" -> partitionRef.domain, "partition" -> partitionRef.partitionName)

    new SimpleDbPartition(this, partitionRef, domain)
  }
  
  
  private def createDomain(domainName:String):Domain = {    
    log.info("creating domain: %s", domainName)
    // create the domain in simpledb
    val domain = account domain(domainName)  
    domain.create
    
    // we track current domains internally too
    domains += (domainName -> domain) 
    
    domain
  }
  
  def deleteDomain(domain:String) {
    for {
      (domainName, simpleDbDomain) <- domains
      if (domainName == domain)} {
        domains -= domainName
        log.info("deleting domain: %s", domain)
        simpleDbDomain delete
      }
  }
  
  /** note: does not delete objects in the partitions */
  def deletePartitions(domain:String, matchFn:PartialFunction[String,Boolean]) {
     for {partitionName <- allPartitions(domain)} {
      if (matchFn.isDefinedAt(partitionName) && matchFn(partitionName)) {
        deletePartition(PartitionRef(domain, partitionName))
      }
    }
  }

  /**  */
  def deletePartition(partitionRef: PartitionRef) {
    log.trace("deletePartition: %s", partitionRef)
    partitionsDomain item(partitionRef.compositeId) clear;
  }
  
  def allPartitions(domain:String):Iterable[String] = {
    val query = String.format("itemName() from _partitions where itemName() like '%s.%%'", domain)
      
    for {item <- account.select(query)}
      yield item.name
  }
  
}

case class SimpleDbAccount(var accessKeyId:String, var secretAccessKey:String)