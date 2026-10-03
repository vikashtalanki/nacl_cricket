/**
 * NACL stats — generic key/value JSON store for stats.html
 * (powers the Summary "Save Season" feature; Rankings can reuse it later with key='rankings').
 *
 * ── One-time setup ────────────────────────────────────────────────────────────
 *  1. Go to https://script.google.com → New project → paste this whole file.
 *  2. Edit setPin() below to your own PIN, then Run → setPin once (grant permissions).
 *     (This stores the PIN as a Script Property so it never ships to the browser.)
 *  3. Deploy → New deployment → type "Web app":
 *        Execute as:      Me
 *        Who has access:  Anyone
 *     Click Deploy, authorize, and copy the "/exec" Web app URL.
 *  4. Paste that URL into stats.html as the value of `script_url` (near the top).
 *
 * ── How the browser talks to it ───────────────────────────────────────────────
 *  Reads (cross-origin from GitHub Pages) use JSONP:
 *     GET  ?action=get&key=summary&callback=foo   ->  foo({ok:true, data:{...}})
 *  Writes avoid a CORS preflight by POSTing text/plain JSON:
 *     POST body: {"action":"save","key":"summary","pin":"....","data":{...}}
 *
 * Storage is a Google Sheet (auto-created on first write) so values can be large
 * and are human-readable / recoverable. Its id is remembered in Script Properties.
 */

var PROP = PropertiesService.getScriptProperties();

function doGet(e) {
  var params = (e && e.parameter) || {};
  var action = params.action || 'get';
  var out;
  try {
    if (action === 'get') {
      var raw = readValue(params.key || 'summary');
      out = { ok: true, key: params.key || 'summary', data: raw ? JSON.parse(raw) : null };
    } else {
      out = { ok: false, error: 'unknown action' };
    }
  } catch (err) {
    out = { ok: false, error: String(err) };
  }
  return respond(out, params.callback);
}

function doPost(e) {
  var out;
  var callback = e && e.parameter && e.parameter.callback;
  try {
    var body = JSON.parse(e.postData.contents);
    if (body.action !== 'save') throw new Error('unknown action');
    var pin = PROP.getProperty('PIN');
    if (pin && String(body.pin) !== String(pin)) throw new Error('bad pin');
    writeValue(body.key || 'summary', JSON.stringify(body.data || {}));
    out = { ok: true };
  } catch (err) {
    out = { ok: false, error: String(err) };
  }
  return respond(out, callback);
}

/** Wrap as JSONP when a callback is supplied, else return plain JSON. */
function respond(obj, callback) {
  var json = JSON.stringify(obj);
  if (callback) {
    return ContentService.createTextOutput(callback + '(' + json + ')')
      .setMimeType(ContentService.MimeType.JAVASCRIPT);
  }
  return ContentService.createTextOutput(json)
    .setMimeType(ContentService.MimeType.JSON);
}

/** Backing store = first sheet of a dedicated spreadsheet (created on demand). */
function store_() {
  var id = PROP.getProperty('SHEET_ID');
  var ss;
  if (id) {
    ss = SpreadsheetApp.openById(id);
  } else {
    ss = SpreadsheetApp.create('NACL stats datastore');
    PROP.setProperty('SHEET_ID', ss.getId());
  }
  return ss.getSheets()[0];
}

function readValue(key) {
  var sh = store_();
  var data = sh.getDataRange().getValues();
  for (var i = 0; i < data.length; i++) {
    if (data[i][0] === key) return data[i][1];
  }
  return null;
}

function writeValue(key, value) {
  var sh = store_();
  var data = sh.getDataRange().getValues();
  for (var i = 0; i < data.length; i++) {
    if (data[i][0] === key) {
      sh.getRange(i + 1, 2).setValue(value);
      return;
    }
  }
  sh.appendRow([key, value]);
}

/** Run this ONCE after pasting (edit the PIN first). */
function setPin() {
  PROP.setProperty('PIN', 'CHANGE_ME_1234');
}
