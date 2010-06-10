function subscribe(settingsFn) {
  var connection = $sync.connect("http://localhost:8080/demo/sync", 
      {authorization:"guest", connected:subscribe});

  function subscribe() {
    connection.subscribe("settings", "demos", settingsFn);
  }
}
