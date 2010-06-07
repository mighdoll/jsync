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

/*
 *  Debug logging and assertions
 *
 *  provides simple logging functions and provides a listener interface to tap into
 *  the stream of logged messages.
 *
 *  var log = $log.getLogger("myModule")
 *
 *  log.log(msg)   - no priority specified for this message, prefer a prioritized log
 *                   variant below
 *  log.debug(msg) - extra information for developers
 *  log.info(msg)  - significant occurence in normal operation
 *  log.warn(msg)  - problem, but workaround available
 *  log.error(msg) - serious error
 *  log.assert(test, msg) - invokes log.error(msg), if test is false-y
 *
 *  $log.getLogger(name) returns a logger that acts just like $log, except that:
 *
 *  (1) It can be selectively enabled or disabled via:
 *    logger.enable()
 *    logger.disable()
 *
 *  (2) It initializes its enabled state to the value of the 'log{Name}'
 *  query parameter, if this is present; and:
 *
 *  (3) It can prefix its messages by the name of the logger, or by a
 *  custom string:
 *    logger.prefix(true); // use the name of the logger
 *    logger.prefix('prefix:');
 *    logger.prefix(false);
 *
 *  LATER, a standard listener to ajax-send the messages up to the server
 *  LATER, filters to turn on/off some messages (getLogger() and enable()
 *  can be used for this)
 *  LATER, enabled/disabled by priority class.
 */
var $log = (function() {
  var listeners = {};
  var logLevels = {detail:0, debug:1, info:2, log:3, warn:4, error:5};
  var instances = {};
  var self = {
    registerListener: registerListener,
    _listeners: listeners,
    _level: 1,
    logger: logger,
    setLevel: function(level) { this._level = logLevels[level]; return this; }
  };

  createLogFunctions();
  registerConsoleListeners();

  return self;


  /** register a listener to be called when $log functions are called */
  function registerListener(thisObject, listenFn, logFunctionName) {
    var registered = listeners[logFunctionName] || (listeners[logFunctionName] = []);
    registered.push(bind(listenFn, thisObject));
  }

  /**
   * notify the listeners that a log function was called.
   *
   * @param args - function arguments array passed to all called functions
   */
  function notifyListeners(logFunctionName, args) {
    var fns = listeners[logFunctionName] || [];
    for (var i = 0; i < fns.length; i++)
      fns[i].apply(null, args);
  }

  /** set up $log functions.  They simply forward to registered
   * functions in the listener array */
  function createLogFunctions() {
    for (var fnName in logLevels) {
      listeners[fnName] = [];
      self[fnName] = createLogFunction(fnName);
    }

    /** return a function that calls the listeners for a given
     * logFunction name */
    function createLogFunction(name) {
      return function() {
        if (logLevels[name] >= this._level)
          notifyListeners(name, arguments);
      };
    }
  }

  /** set up listeners for the firebug console.  */
  function registerConsoleListeners() {
    // set up a listener for the console functions that match the $log functions
    if (typeof window.console != 'undefined') {
      for (var fnName in logLevels) {
        if (typeof console[fnName] == 'function') {
          registerListener(console, console[fnName], fnName);
        } else {
          registerListener(console, console.log, fnName);
        }
      }
    }
  }

  function logger(name, enabled) {
    var log = instances[name] =
      instances[name] || createLogger(this, name, enabled !== undefined ? enabled : true);
    return log;
  }

  function createLogger(parent, name, enabled) {
    var override = document.location.search.match(
      RegExp('\\blog[_-]?' + name + '(?:=([^&]*))?\\b', 'i'));
    enabled = override
      ? (override[1]===undefined ? true : ['0','false'].indexOf(override[1]) < 0)
      : (enabled || false);
    var prefix = false;

    var instance = {
      enable:function() { enabled=true; return this; },
      disable:function() { enabled=false; return this; },
      setPrefix:function(prefix_) { prefix=prefix_; return this; },
      _level: 0,
      setLevel: self.setLevel, // shares implementation; doesn't delegate
      assert: self.assert
    };
    // add the delegators
    for (var methodName in logLevels)
      instance[methodName] = (function(methodName){
          return function() {
            if (enabled && logLevels[methodName] >= this._level) {
              var args = arguments;
              if (prefix) {
                args = Array.prototype.slice.call(arguments, 0);
                args.unshift(prefix === true ? name + ':' : prefix);
              }
              parent[methodName].apply(parent, args);
            }
          };
        })(methodName);
    return instance;
  }

  /** return a fn as a bound method of thisObject */
  function bind(fn, thisObject) {
    return function() { return fn.apply(thisObject, arguments); };
  }
})();

// If no console object exists, use $log as a console object.  This
// allows client code without a source dependency on this package to
// use it if it is present (and Firebug lite or some other console
// emulator if it is not -- or to test whether console is present
// before calling its methods).
//
// To prevent an infinite regress it's important that this follow the
// call to setupConsoleListeners in the initialization of $log.
if (typeof window.console == 'undefined')
  window.console = $log;
