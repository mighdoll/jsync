package com.digiting.sync
import com.digiting.util._
import Observers.DataChangeFn
import Partition.Transaction

object RamWatches {
  type PartitionWatchFn = (Seq[DataChange])=>Unit

}

import RamWatches._

/** supports registering functions as watches on a partition.  The watch functions
 * are not persistent, so they're lost if the server is rebooted.
 */
trait RamWatches {
  
  import Log2._
  implicit private val log = logger("RamWatches")

}
