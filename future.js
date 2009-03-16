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

/* A future reference to an event that has not yet occurred.
 *
 * @param resolved - (optional) completion function when trigger is fired
 */
$sync.future = function(resolved) {
    var i,
    that = {
        triggered: false
    },
    completions,
    triggerParam;   // param passed to trigger()

    var init = function() {
        completions = resolved ? [resolved] : [];
    };

    /* trigger the future, causing all registered callbacks to be called
     * @param payload - data that will be passed to all await function */
    that.trigger = function(payload) {
        triggerParam = payload;
        that.triggered = true;
        for (i = 0; i < completions.length; i++) {
            completions[i](payload);
        }
        return this;
    };

    /* remove a registered completion function */
    that.ignoreTrigger = function(func) {
        for (i = 0; i < completions.length; i++) {
            if (completions[i] === func) {
                completions.splice(i,1);
                i--;
            }
        }
        return this;
    };

    /* register an additional function to be called when the future is triggered */
    that.await = function(resolved) {
        if (that.triggered) {
            resolved(triggerParam);
        } else {
            completions.find(resolved) || completions.push(resolved);
        }
        return this;
    };

    init();

    return that;
};

/*
 * Register a callback to be called when one or more futures have all triggered.
 *
 * LATER make this a future instead of a callback.  Seems more orthogonal that way.
 * e.g. groupFuture(futureArray).await(),
 *
 *@param futureArray - array of future objects
 *@param complete - function to call when future objects have triggered
 */
$sync.futuresWatch = function(futureArray, complete) {
    var i, done, future, checkFunc, reg, registered = [],

    checkTriggered = function() {
        var allTriggered = true;
        // see if the futures have already fired.
        for (i = 0; allTriggered && i < futureArray.length; i++) {
            if (!futureArray[i].triggered) {
                allTriggered = false;
            }
        }
        if (allTriggered) {
            // remove waiters that we registered
            for (i = 0; i < registered.length; i++) {
                reg = registered[i];
                reg.future.ignoreTrigger(reg.checkFunc);
            }
            complete();
            return true;
        }
        return false;
    };

    done = checkTriggered();
    if (!done) {
        // conveniently, browser javascript is single threaded, otherwise we'd have to worry about gettings stomped on..'

        // set a completion proc to recheck when any incomplete elements fire again
        for (i = 0; i < futureArray.length; i++) {
            future = futureArray[i];
            if (!future.triggered) {
                // create a wrapper for each element, so that we can remove 'em later
                checkFunc = function() {
                    checkTriggered()
                };
                registered.push({
                    future: future,
                    checkFunc: checkFunc
                });
                future.await(checkFunc);
            }
        }
    }
}


