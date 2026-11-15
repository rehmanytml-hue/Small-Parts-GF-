/* Gives the page the same small database interface it used inside Claude, backed by Firebase Firestore.
   Each document is listened to once and then read from that live copy, so the live view costs few reads. */
(function () {
  'use strict';
  var ready = null;
  var start = function () {
    var cfg = window.FIREBASE_CONFIG;
    if (!cfg || !cfg.apiKey || /PASTE/.test(cfg.apiKey) || !window.firebase) return Promise.resolve(null);
    var fs;
    try {
      window.firebase.initializeApp(cfg);
      fs = window.firebase.firestore();
    } catch (e) { return Promise.resolve(null); }
    var cache = {};
    var entry = function (path) {
      if (!cache[path]) {
        var e = { snap: null, first: null };
        e.first = new Promise(function (resolve, reject) {
          fs.doc(path).onSnapshot(function (s) { e.snap = s; resolve(s); }, function (err) { delete cache[path]; reject(err); });
        });
        cache[path] = e;
      }
      return cache[path];
    };
    var db = {
      doc: function (path) {
        return {
          get: function () { var e = entry(path); return e.first.then(function () { return e.snap; }); },
          set: function (value) { return fs.doc(path).set(value); }
        };
      }
    };
    var signIn;
    try { signIn = window.firebase.auth().signInAnonymously().catch(function () {}); } catch (e) { signIn = Promise.resolve(); }
    return signIn.then(function () { return db; });
  };
  window.claude = {
    use: function (name) {
      if (name !== 'db') return Promise.resolve(null);
      ready = ready || start();
      return ready;
    }
  };
})();
