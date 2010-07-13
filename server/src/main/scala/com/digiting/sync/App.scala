package com.digiting.sync
import com.digiting.util.Log2._
import scala.util.DynamicVariable
import com.digiting.util._

/** thread local access to the currently running app context */
object App {
  implicit private lazy val log = logger("App")
  val current = new DynamicVariable[Option[AppContext]](None)
  def currentAppName:String = current.value match {
    case Some(app) => app.appName
    case _ => "<no-current-app>"
  }
  
  def withTransientPartition[T](fn: =>T):T = {
    current.value match {
      case Some(app:AppContext) => app.withTransientPartition(fn)
      case _ => 
        throw new ImplementationError()
    }  
  }
  def app:AppContext = current.value getOrElse {abort2("App.app() - no current app")}
}

trait HasTransientPartition { 
  val transientPartition:Partition

  def withTransientPartition[T] (fn: =>T):T = {
    SyncManager.currentPartition.withValue(transientPartition) {
      fn
    }
  }  
}
class GenericAppContext(connection:Connection) extends AppContext(connection) {
  val appName = "generic-app-context"
}

object TempAppContext {
  def apply(name:String):AppContext = {
    new {val appName = name} with AppContext(new Connection(name + "-conn"))
  }
}

/** App with the ability to connect syncable message queues to annotated service endpoints.  */
abstract class RichAppContext(connection:Connection) extends AppContext(connection) with ImplicitServices
