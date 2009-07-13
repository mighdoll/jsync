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
 *  LATER find an existing library to replace this one.
 */
var $debug = (function() {
  // Firebug and recent Safari, at least
  var useNativeConsole = typeof window.console !== "undefined" && typeof console.log == 'function';
  var $console; // HTML element to log to, when not using the native console
  var messages = []; // messages queued before document load
  var self = {
    assert: function(val, msg) {
      if (!val) {
        self.log("ASSERT: " + (msg || ""));
        debugger;
      }
    },
    
    fail: function(msg) {
      self.log("FAIL: " + msg);
      debugger;
    },
    
    // init() installs code that replaces this once on document load
    log: function(msg) {
      messages.push(msg);
    },
    
    error: function(msg) {
      self.log("ERROR: " + msg);
    },
    
    info: function(msg) {
      self.log("INFO: " + msg);
    },
    
    warn: function(msg) {
      self.log("WARN: " + msg);
    }
  };
  
  // if we're using the native console, delegate these to the console
  var names = ['log', 'error', 'info', 'warn'];
  for (var i = 0; i < names.length; i++) {
    var name = names[i];
    if (useNativeConsole) 
      self[name] = function() {
        console[name].apply(console, arguments);
      }
  }
  if (window.location.search.match(/[?&]log=false/))
    self.log = function() {}
  
  /* setup $debug.log() function.
   *
   * use firefox console if available,
   * otherwise create a div named #console.
   *
   * LATER - log these to the server.
   */
  function initDivConsole() {
    $console = $("#console");
    if ($console.length === 0) {
      $("body").append("<div id='console'></div>");
      $console = $("#console");
    }
    self.log = logToDiv;
    self.log("Console:");
    while (messages.length) 
      $debug.log(messages.shift())
  }
  
  function logToDiv(msg) {
    // FIXME escape for HTML
    $console.append("<div>" + msg + "</div>");
  };
  
  if (!useNativeConsole) 
    $(document).ready(initDivConsole);
  
  return self;
})();
