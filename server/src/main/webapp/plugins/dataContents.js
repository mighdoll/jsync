
(function($) {
  var log = $log.logger("dataContents");
  /**
   * Bind a liquid data property to a DOM element. The contents of the DOM element will
   * be replaced with a rendered version of the data object, and updated whenever the
   * property changes.  
   * 
   * To bind a property named foo on object obj, use e.g. 
   * <code>
   *    $().dataContents({
   *      data: obj.fooChanges()
   *      render: myRenderFn
   *    });
   * </code>
   * 
   * Model data is converted to html via a user supplied rendering function:
   * renderFn(model). renderFn should return an html fragment.
   * 
   * There are several ways to specify a rendering function. In priority order:
   * <ol>
   * <li>render:renderFn} -> provide a rendering function in the options hash
   * <li>render:{'myKind':renderFn} -> provide a map of rendering functions
   * (this is useful for widgets that contain multiple instances. The keys to
   * the map are liquid object kinds, or "string", "number", or "undefined"  
   * </ol>
   */
  $.widget("lq.dataContents", {
    options:{
      data:null,             // required, Changes object that tracks changes
      render:defaultRender   // optional renderFn, or map of kind -> renderFn.
    },
    _create:function() {
      this.options.data || log.error("option.data is required");
     },
    _init:function() {       
       var $elem = this.element;
       var options = this.options;
       var render = options.render;
       if ($.isPlainObject(render)) {
         render = renderMapFn(options.render);
       }
       $debug.assert(typeof options.data !== 'function');
       options.data.watch(replaceContents);
       
       function replaceContents(change) {
         $elem.html(render(change.target, change));
       }
    }
  });
  
  // by default we just render the property into html directly
  function defaultRender(obj, change) {    
    return obj[change.property];
  }
  
  // if a map of kind -> fn is specified, we use that to render
  function renderMapFn(map) {
    return function(obj, change) {
      var value = obj[change.property];      
      var entry = map[renderKind(value)];
      if ($.isFunction(entry)) {
        return entry(obj, change);
      } else if (!value) {
        return "";  // ok to not have a map entry for undefined or null values
      } else {
        log.error("render function not defined for obj: ", obj);
        return "?";
      }
    };
    
    // return the $kind of the value, or "string", "undefined", or "number"
    function renderKind(value) {
      var typeString = typeof value;
      if (/(string)|(undefined)|(number)/.test(typeString)) {
        return typeString;
      }
      return value.$kind;
    }
  }
})(jQuery);

