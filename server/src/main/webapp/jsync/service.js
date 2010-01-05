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
var $sync = $sync || {};

$sync.service = function(serviceName, messageCreateFn) {
  var queue;
  
  $sync.manager.withNewIdentity({
    partition: ".implicit",
    id: serviceName
  }, function() {
    queue = $sync.sequence();
  });
  
  var self = {
    sendForm: function(formOrEvent) {
      var message;
      if (formOrEvent.target) { // TODO, more robust way to distinguish forms from events
        message = formToMessage(formOrEvent.target, messageCreateFn);
      } else {
        message = formToMessage(formOrEvent, messageCreateFn);
      }
      queue.append(message);
      return false; // so that it can be used directly in a click or onsubmit handler
    }
  };
  
  function formToMessage(form, createMessage) {
    var message = $sync.manager.withTransientPartition(messageCreateFn);
    $sync.manager.eachPropertyName(message, function(prop) {
      var value = form[prop].value;
      var type = form[prop].type;
      if (type == 'checkbox' || type == 'radio') {
        if (form[prop].checked) 
          value = "true";
        else 
          value = "false";
      }
      message.setProperty(prop, value);
    });
    return message;
  }
  
  return self;
};

/** 
 * Connect an html form to syncable service endpoint.  Automatically converts the argments.
 * 
 * Pass an optional function that's called when/if the server sends a response.
 * Returns a function suitable for a submit form handler
 */
$sync.serviceForm = function(responseFn) {
  var log = $log.getLogger("serviceForm");
  
  /** called by a submit handler to submit this form to a sync service endpoint */
  function processForm(formOrEvent) {
    var serviceName, messageQueue, message, target, form;
    
    target = formOrEvent.target;
    form = target && target.tagName === 'FORM' && target;
    if (form) { // TODO, more robust way to distinguish forms from events
      serviceName = form['serviceName'] && form['serviceName'].value;
      if (serviceName && serviceName != "") {
        messageQueue = $sync.manager.withNewIdentity({
          partition: ".implicit",
          id: serviceName
        }, $sync.sequence);
        
        message = formToServiceCall(form)
        messageQueue.append(message);
        $sync.observation.watchProperty(message, 'response', function(change) {
          $log.assert(change.target == message);
          responseFn(change.target.response);
        });
      }
      else {
        log.error("can't find serviceName in form: ", formOrEvent.target);
      }
      return false; // so this can be used directly in a click or onsubmit handler
    }
  }

  /**
   * convert a form to ServiceCall message.  The method parameters are identified by
   * form fields with names param-0, param-1, etc. 
   * returns the ServiceCall object
   */
  function formToServiceCall(form) {    
    var message = $sync.manager.withTransientPartition($sync.serviceCall);
    var params = $sync.manager.withTransientPartition($sync.sequence);
    var paramDex = 0;
    var foundParam = false;
    var formElem;
    var paramString;
    do {
      formElem = form['param-' + paramDex];
      if (formElem) {
        paramString = $sync.manager.withTransientPartition($sync.stringParameter);
        paramString.string_(formElem.value);
        params.append(paramString);
        foundParam = true;
      } else {
        foundParam = false;
      }
      paramDex += 1;
    } while (foundParam);
    
    message.parameters_(params);
    return message;
  }
  
  return processForm;
};
