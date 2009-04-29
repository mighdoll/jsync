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
$debug = (function() {
    /* TODO find some existing debug console library to use.. */
    var that = {},
    $console;

    var init = function() {
        initConsole();
    };

    that.assert = function(val, msg) {
        if (!val) {
            that.log("ASSERT: " + (msg || ""));
            debugger; } };

    that.warn = function(msg) {
        that.log("WARN: " + msg); };

    that.error = function(msg) {
        that.log("ERROR: " + msg); };

    that.fail = function(msg) {
        that.log("FAIL: " + msg); debugger; };

    /* setup $debug.log() function.
     *
     * use firefox console if available,
     * otherwise create a div named #console.
     *
     * LATER - log these to the server.
     */
    var initConsole = function() {
        if (typeof window.console !== "undefined") {
            that.log = function() { 
                console.log.apply( console, arguments )
            };
        } else {
            $(document).ready(function() {
                // LATER I suppose we should also queue messages logged before the document is ready..
                $console = $("#console");
                if ($console.length === 0) {
                    $("body").append("<div id='console'></div>");
                }
                $console = $("#console");
                that.log = logToDiv;
                that.log("Console:");
            });
        }
    };

    var logToDiv = function(msg) {
        $console.append("<div>" + msg + "</div>");
    };

    init();

    return that;
})();
