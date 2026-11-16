/* Gives the page the same small database interface it used inside Claude, backed by Firebase Firestore.
   It keeps working with no connection: Firestore keeps a copy on the phone, a second copy is kept in localStorage,
   and writes made offline are sent when the connection returns. */
(function () {
  'use strict';
  var ready = null;
  var MP = 'hr-mirror:';
  var mget = function (p) { try { var v = localStorage.getItem(MP + p); return v ? JSON.parse(v) : null; } catch (e) { return null; } };
  var mset = function (p, v) { try { localStorage.setItem(MP + p, JSON.stringify(v)); } catch (e) {} };
  var mdel = function (p) { try { localStorage.removeItem(MP + p); } catch (e) {} };
  var fakeSnap = function (v, final) { return { exists: v != null, data: function () { return v; }, metadata: { fromCache: !final } }; };
  var after = function (ms, v) { return new Promise(function (r) { setTimeout(function () { r(v); }, ms); }); };

  /* Used only when Firebase cannot start at all: data stays on this phone. */
  var localDb = {
    doc: function (path) {
      return {
        get: function () { return Promise.resolve(fakeSnap(mget(path), true)); },
        set: function (value) { mset(path, value); return Promise.resolve(); }
      };
    }
  };

  var start = function () {
    var cfg = window.FIREBASE_CONFIG;
    if (!cfg || !cfg.apiKey || /PASTE/.test(cfg.apiKey) || !window.firebase) return Promise.resolve(localDb);
    var fs, persisted = Promise.resolve();
    try {
      window.firebase.initializeApp(cfg);
      fs = window.firebase.firestore();
      try { fs.settings({ cacheSizeBytes: window.firebase.firestore.CACHE_SIZE_UNLIMITED }); } catch (e) {}
      persisted = fs.enablePersistence({ synchronizeTabs: true }).catch(function () {});
    } catch (e) { return Promise.resolve(localDb); }
    var cache = {};
    var entry = function (path) {
      if (!cache[path]) {
        var e = { snap: null, first: null };
        e.first = new Promise(function (resolve, reject) {
          fs.doc(path).onSnapshot(function (s) {
            e.snap = s;
            if (s.exists) mset(path, s.data());
            else if (!(s.metadata && s.metadata.fromCache)) mdel(path);
            resolve(s);
          }, function (err) { delete cache[path]; reject(err); });
        });
        cache[path] = e;
      }
      return cache[path];
    };
    var db = {
      doc: function (path) {
        return {
          get: function () {
            var e = entry(path);
            /* A "missing" answer that only came from the phone's cache is overruled by the copy kept in localStorage. */
            var pick = function (snap) {
              if (snap && !snap.exists && snap.metadata && snap.metadata.fromCache) { var m = mget(path); if (m != null) return fakeSnap(m); }
              return snap;
            };
            if (e.snap) return Promise.resolve(pick(e.snap));
            var saved = mget(path);
            var live = e.first.then(function () { return pick(e.snap); });
            /* With a slow or missing connection, answer from the copy kept on the phone rather than waiting. */
            return saved == null ? live : Promise.race([live, after(2500, fakeSnap(saved))]);
          },
          set: function (value) {
            mset(path, value);
            var p = fs.doc(path).set(value);
            /* Offline writes are queued by Firestore and only finish when the server answers, so do not wait for that. */
            p.catch(function () {});
            return Promise.race([p, after(2000)]);
          }
        };
      }
    };
    /* The public live link only reads, so it does not sign in. */
    var liveOnly = window.HR_WEB_LIVE_ONLY === true || /[?&#]live(\b|=)/.test(location.search + location.hash);
    var trySignIn = function () {
      try {
        if (window.firebase.auth().currentUser) return Promise.resolve();
        return window.firebase.auth().signInAnonymously().catch(function () {});
      } catch (e) { return Promise.resolve(); }
    };
    var signIn = Promise.resolve();
    if (!liveOnly) {
      signIn = Promise.race([trySignIn(), after(5000)]);
      /* If the first start had no connection, sign in once it is back so queued writes are accepted. */
      window.addEventListener('online', function () { trySignIn(); });
    }
    return persisted.then(function () { return signIn; }).then(function () { return db; });
  };
  window.claude = {
    use: function (name) {
      if (name !== 'db') return Promise.resolve(null);
      ready = ready || start();
      return ready;
    }
  };
})();
