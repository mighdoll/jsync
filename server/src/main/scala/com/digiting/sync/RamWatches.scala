package com.digiting.sync
import com.digiting.util._
import java.util.concurrent.ConcurrentHashMap
import Observers.DataChangeFn
import RandomIds.randomUriString
import Partition.Transaction


/** supports registering functions as watches on a partition.  The watch functions
 * are not persistent, so they're lost if the server is rebooted.
 */

trait RamWatches extends Partition with LogHelper {
  val watchFns = new ConcurrentHashMap[RequestId, DataChangeFn]
  
  def watchFn(fn:DataChangeFn, duration:Int):PickledWatch = {
    val requestId = RequestId(randomUriString(8))
    watchFns.put(requestId, fn)
    val clientId = ClientId(Observers.currentMutator.value)
    new PickledWatch(clientId, requestId, System.currentTimeMillis + duration)
  }
  
  def watch(id:InstanceId, fn:DataChangeFn, duration:Int) {
    watch(id, watchFn(fn, duration))
  }
  
  override def notify(watch:PickledWatch, change:DataChange, tx:Transaction) {
    super.notify(watch, change, tx)
    
    val fn = watchFns get(watch.requestId) 
    if (fn != null) {
      fn(change)
    } else {
      err("notify() can't find fn for %s  for change:%s", watch, change)
    }
  }  
}
