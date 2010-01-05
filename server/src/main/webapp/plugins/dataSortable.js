/*
 *   Copyright [2009] Digiting Inc
 *
 * DataSortable.
 *
 * Depends:
 *   ui.sortable.js
 *
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

/**
 * Options:
 * 	model -- a syncable sequnce
 *  updated - called when the underlying model changes and after the control has been re-rendered
 */

(function($){
  var CHILD_SELECTOR_CLASS = '.dataSortable-item';

  // The CSS class that marks a dataSortable
  var DATA_CONTAINER_CLASS = 'dataSortable-container';
  var DATA_CONTAINER_SELECTOR = '.' + DATA_CONTAINER_CLASS;

  // The data() key that retrieves the syncable model from a sortable
  // container or one of its items.
  var DATA_MODEL_KEY = 'model';

  // stores the model behind a draggable item as it's being dragged
  var draggableSourceModel;

  // Make the target into a sortable whose items are synced with
  // options.model. options.model should be a syncable sequence.
  $.fn.dataSortable = function(options) {
    if (typeof options === 'string') {
      // this is the only option we presently handle
      $debug.assert(options === 'destroy');
      var model = options.model;
      var watcher = this.data('dataSortable-watcher');
      $sync.observation.ignore(model, watcher);
      $(this).find(CHILD_SELECTOR_CLASS).remove();
      this.sortable('destroy');
      return this;
    }
    // Turn the targets into dataSortables. options should be a hash.
    checkOptions(options);
    this.each(function() { createDataSortable($(this), options); });
    // the following line works around a bug in jQuery UI as
    // documented here: http://tinyurl.com/lje2eb
    this.sortable('refresh');
    return this;
  };

  // Make the target into a container whose items' draggables are
  // synced with options.model. options.model should be a syncable
  // sequence.
  $.fn.dataList = function(options) {
    checkOptions(options);
    return this.each(function() { createDataList($(this), options); });
  };

  // If this element is a dataSortable, return its model. If it's an
  // element rendered from a sequence item within a dataSortable's
  // model, return the model for that sequence item.
  $.fn.dataModel = function() {
    return this.data(DATA_MODEL_KEY);
  };

  // This element should be an element within a dataSortable.  Return
  // the data model for its container.
  $.fn.dataSortableContainer = function() {
    return this.parent(DATA_CONTAINER_SELECTOR).dataModel();
  };

  // Find a DOM element that was rendered from a given model within
  // the sequence.  Return undefined otherwise.
  $.fn.renderedModel = function(model) {
    var found;
    $.each($(this).children(CHILD_SELECTOR_CLASS), function(index, child) {
       if ($(child).dataModel() === model) {
         found = child;
         return false;
       }
    });
    return found;
  };

  // Helper for dataSortable, dataList
  function checkOptions(options) {
    $debug.assert(options.model, 'options.model is required');
    $debug.assert(options.model.kind === "$sync.sequence",
                  "options.model must be a sync sequence");
    // FIXME copy options before modifying
    options.render = options.render || function(model) {
      return '<div>' + String(model) + '</div>';
    };
  }

  function createDataSortable(container, options) {
    var $container = $(container);
    var model = options.model;
    $container.addClass(DATA_CONTAINER_CLASS);
    $container.data(DATA_MODEL_KEY, model);
    // These options override the specified options, in the call to
    // the $.fn.sortable.
    var overrideOptions = {
      // respond to an update to the view by detecting whether an
      // element was inserted, deleted, or moved; and updating the
      // view's model
      update: function(event, ui) {
        var $item = ui.item;
        if ($item.hasClass('ui-draggable'))
          $item.data(DATA_MODEL_KEY, draggableSourceModel);
        var item = $item.dataModel();
        var pos = $container.children(CHILD_SELECTOR_CLASS).index($item);
        if (!(pos >= 0)) {
          // item is being dragged out of this list into another list
          model.remove(item);
        } else if (model.contains(item)) {
          // item is being moved within this list
          model.move(item, pos);
        } else {
          // item is being moved or copied into this list from another
          // list
          var insertItem = item;
          if (options.modelTranslator)
            insertItem = options.modelTranslator(item) || insertItem;
          if (options.rerender || item !== insertItem)
            $item.remove();
          var prev = null;
          model.each(function(item, i) { if (i == pos-1) prev = item; });
          model.insert(insertItem, prev);
        }
      }
    };
    var settings = $.extend({}, defaultOptions, options, overrideOptions);
    $container.sortable(settings);
    bindDataContainer($container, options, false);
  }

  function createDataList(container, options) {
    var $container = $(container);
    var model = options.model;
    $container.addClass(DATA_CONTAINER_CLASS);
    $container.data(DATA_MODEL_KEY, model);
    bindDataContainer($container, options, true);
  }

  var defaultOptions = {};

  function bindDataContainer($container, options, draggableItems) {
    var seq = options.model;
    updateThisViewList();
    $container.data('dataSortable-watcher', updateThisViewList);
    $sync.observation.watch(seq, updateThisViewList);
    function updateThisViewList(change) {
      updateViewList($container, options, draggableItems);
    }
  }

  // Update $container's children from the model's sequence items.
  // The renderer is applied to new sequence items, and the resulting
  // elements are inserted.  Elements whose models are no longer
  // present in the sequence are removed.  Elements whose models have
  // changed position within the sequence are moved within the
  // container.  (They are not deleted and then re-rendered.)
  //
  // This function is called once upon dataSortable creation, and once
  // for each notification posted to the sequence model.
  function updateViewList($container, options, draggableItems) {
    var seq = options.model;
    checkForDuplicates(seq);
    var renderers = options.render;
    // map is a hash {id => {view => Element, pos => integer}}
    var map = createChildMap();
    // walk each item in the model, inspecting whether its view
    // is present in $container at the right index
    var seen = {};
    seq.each(function(model, i) {
        // this works around a bug in the current system, where an
        // item is present twice once added
        if (seen[model.id]) return;
        seen[model.id] = true;
        var entry = map[model.id];
        // if there's already a view in the correct position, do nothing
        if (entry && entry.pos == i) return;
        // create a new view, or move the existing one
        var view = entry ? entry.view : render(model).addClass('dataSortable-item');
        if (i === 0)
          $container.prepend(view);
        else
          $($container.children(CHILD_SELECTOR_CLASS).get(i-1)).after(view);
        // Newly inserted view?
        if (!entry) {
          // The previous value of `view` no longer refers to the copy
          // in the DOM, so applying data() to it won't affect the DOM
          // element.  Thus read view from the DOM element, instead of
          // using the 'view' variable.
          //
          // TODO is this still necessary? the call to $() inside render()
          // may have obsoleted this
          //console.info($container.children(CHILD_SELECTOR_CLASS).get(i) === view);
          var $view = $($container.children(CHILD_SELECTOR_CLASS).get(i));
          handleInsertedView($view, model, options, draggableItems);
        }

        // Rebuild the child map, now that the positions have changed.
        // (This is cheaper than trying to update the existing map.)
        map = createChildMap();
      });
    // delete views whose models are no longer present
    seq.each(function(model) { map[model.id].seen = true; });
    $.each(map, function(_, entry) {
        if (entry.seen) return;
        $(entry.view).remove();
      });

    // notify listener to updated
    options.updated && options.updated();

    // Create and return a hash map of the form documented at the top
    // of the enclosing function.
    function createChildMap() {
      var map = {};
      $.each($container.children(CHILD_SELECTOR_CLASS), function(i, view) {
          map[$(view).dataModel().id] = {view: view, pos: i};
        });
      return map;
    }

    // Create the DOM HTML or HTML string with which to render
    // the sequence element `model`.
    function render(model) {
      var renderfn =
        typeof(renderers) === 'function'  ? renderers : renderers[model.kind];
      return renderfn
        ? $(renderfn(model))
        : $('<div/>').text(String(model));
    }

    // `model` is a sequence element, and $view is the jQuery element
    // that was rendered for it.  $view is now in the DOM: hook them
    // up.
    function handleInsertedView($view, model, options, draggableItems) {
      $view.data(DATA_MODEL_KEY, model);
      if (draggableItems) {
        var itemOptions = $.extend({connectToSortable: options.connectWith},
                                   options.itemOptions || {});
        // TODO wrap the passed helper
        itemOptions.helper && console.error('unimplemented');
        itemOptions.helper = draggableHelper;
        $view.draggable(itemOptions);
      }
      if (model.children) {
        $view.append('<div class="childContainer"></div>');
        var $childContainer = $('.childContainer', $view);
        $childContainer.dataSortable({model: model.children});
      }
      if (options.inserted)
        options.inserted($view);

      // This function is used as the value of the 'helper' attribute
      // in draggable options for an inserted view. It capture the
      // model so that it can be dropped -- since the clone doesn't
      // otherwise , contain the model.  This function is otherwise
      // identical to the 'clone' value of the 'helper' option to
      // $.draggable().
      function draggableHelper() {
        draggableSourceModel = $view.dataModel();
        var $clone = $view.clone();
        $clone.data(DATA_MODEL_KEY, draggableSourceModel);
        return $clone;
      }
    }
  }

  // Check the integrity of container. This detects problems in the
  // model before they become more-difficult-to-diagnose problems in
  // the view code (which assumes containers don't contain
  // duplicates).
  function checkForDuplicates(container) {
    var duplicates = [];
    var seen = {};
    container.each(function(item) {
      var id = item.id;
      if (id in seen) duplicates.push(item);
      seen[id] = true;
    });
    if (duplicates.length)
      $log.error('The following ' + duplicates.length +
                 ' items occurred more than once in the ' + container.size() +
                 ' items in', container, ':', duplicates);
  }
 })(jQuery);
