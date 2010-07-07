package com.digiting.sync
import com.digiting.util._

/** apply change descriptions to the local store*/
object UpdateLocalContext {
  
	private def modify(app:AppContext, change:DataChange) {
    change match {
      case created:CreatedChange => NYI()
      case property:PropertyChange => NYI()
      case deleted:DeletedChange => NYI()
      case insertAt:InsertAtChange => NYI()
      case removeAt:RemoveAtChange => NYI()
      
      case move:MoveChange => NYI()
      case putMap:PutMapChange => NYI()
      case removeMap:RemoveMapChange => NYI()
      case put:PutChange => NYI()
      case remove:RemoveChange => NYI()
      case clear:ClearChange => NYI()
    }
  }
   
}
