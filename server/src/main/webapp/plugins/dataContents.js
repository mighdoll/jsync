(function($) {
  /**
   * Models are converted to html via a user supplied rendering function:
   * renderFn(model). renderFn should return html content.
   * 
   * There are several ways to specify a the renderFn. In priority order:
   * <ol>
   * <li>render:renderFn} -> provide a rendering function in the options hash
   * <li>render:{'myKind':renderFn} -> provide a map of rendering functions
   * (this is useful for widgets that contain multiple instances. The keys to
   * the map are liquid object kinds.
   * <li>$.fn.myDataWidget.defaults.renderMap = {'myKind':renderFn} -> a
   * default global map for all myDataWidgets.
   * </ol>
   */

  /**
   * Bind liquid data to a DOM element, or property The contents of the DOM element will
   * be replaced with a rendered version of the data object.  The DOM element
   * will be redrawn whenever the data object is changed.
   */
  $.fn.dataContents = function(model, options) {
    var settings = $sync.widget.settings(model, $.fn.dataContents.defaults, options);
    var render = $sync.widget.findRenderFn(model, settings);

    return this.each(function() {
      createDataContents($(this), model, settings);
    });

    function createDataContents($container, model) {
      $container.data(settings.modelDataKey, model);

      replaceContents();
      $sync.observation.watch(model, replaceContents);

      function replaceContents() {
        $container.html(render(model));
      }
    }
  };

  /** global default options for all dataContents widgets */
  $.fn.dataContents.defaults = {};
})(jQuery);

$sync = $sync || {};
$sync.widget = $sync.widget || {};

/** (Used internally by data widgets). calculate default settings for a widget */
$sync.widget.settings = function(model, defaultOptions, options) {
  $debug.assert(model, "model must be defined");
  var opts = $.extend( {}, $sync.widget.defaults, defaultOptions, options);
  if (options && $.isPlainObject(options.render)) {
    // map was provided by the user, convert it to a map
    delete opts.render;
    $.extend(opts.renderMap, options.render);
  }
  return opts;
};

/**
 * (Used internally by data widgets). Find an appropriate renderFn for this kind
 * of model given the supplied options
 */
$sync.widget.findRenderFn = function(model, opts) {
  return opts.render || opts.renderMap[model.$kind] || htmlRenderFn(model)
      || defaultRenderFn;

  /**
   * if the model objects supports toHtml(), return a renderFn that calls
   * toHtml().
   */
  function htmlRenderFn(model) {
    if ($.isFunction(model.toHtml)) {
      return function(m) {
        return m.toHtml();
      };
    } else {
      return undefined;
    }
  }

  /** worst case, we'll just call toString to render the object. */
  function defaultRenderFn(model) {
    return String(model);
  }
};

/** (used internally by data widgets) global defaults for all widgets */
$sync.widget.defaults = {
  // map of {$kind -> renderFn()}. Specifies a model renderer for
  // multiple instances for collection
  renderMap : {},
  // the jquery data() key to get the model object from a dom element
  modelDataKey : "model"
};
