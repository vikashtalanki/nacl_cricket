// ── NACL Ticket System — Google Apps Script ─────────────────────────────────
// Deploy as Web App: Execute as Me, Anyone can access
//
// Sheet ID is extracted from your URL automatically — no changes needed there.
// Only update ADMIN_EMAIL if it ever changes.

var SHEET_ID    = '1O7BH-4fNrQh2cIlulhPS3UjSwCfMkFWmToGYspCAfng';
var SHEET_NAME  = 'Tickets';  // Tab name — will be created if it doesn't exist
var ADMIN_EMAIL = 'northamericacricketleague@gmail.com';

// ── Extra recipients by category ─────────────────────────────────────────────
// When a ticket's Purpose matches a key below, those address(es) are ADDED (as CC)
// to the admin notification, in addition to ADMIN_EMAIL. Leave the map empty to
// disable. Keys MUST match the form's "Purpose" text EXACTLY — one of:
//   'Player Move / Addition / Remove'
//   'Scorecard Update Request'
//   'Issues & Disputes'
//   'Umpiring Complaint'
//   'Ground Issues / Info'
//   'Scheduling Request'
//   'Other'
// You can list more than one address per category.
var CATEGORY_EXTRA_EMAILS = {
  'Other': ['arun198821@gmail.com','abdulmalik.sjsu@gmail.com','mallik.1201@gmail.com','princepardhu1@gmail.com', 'emailrezakhan@gmail.com'],
  'Issues & Disputes': ['arun198821@gmail.com'],
  'Umpiring Complaint': ['arun198821@gmail.com', 'abdulmalik.sjsu@gmail.com'],
  'Player Move / Addition / Remove': ['mallik.1201@gmail.com', 'abdulmalik.sjsu@gmail.com'],
  'Scorecard Update Request': ['princepardhu1@gmail.com', 'mallik.1201@gmail.com'],
  'Ground Issues / Info': ['princepardhu1@gmail.com', 'emailrezakhan@gmail.com']
};

// ── Column order in the sheet ────────────────────────────────────────────────
var COLUMNS = [
  'Ticket ID',
  'Timestamp',
  'Email',
  'Name',
  'Season',
  'Team',
  'Purpose',
  // Player Move fields
  'Request Type',
  'Player Name',
  'From Team',
  'To Team',
  'Remove From Team',
  // Scorecard fields
  'Match',
  'Scorecard Link',
  // Scheduling fields
  'Weekend',
  'Slots to Avoid',
  'Umpire Notes',
  // Generic
  'Description',
  // Always last
  'Notes',
];

// ── Entry point ──────────────────────────────────────────────────────────────
function doGet(e) {
  var params = e.parameter;

  // CORS headers — required for browser fetch
  var output = ContentService.createTextOutput();
  output.setMimeType(ContentService.MimeType.JSON);

  try {
    if (params.action === 'insert') {
      var result = insertTicket(params);
      output.setContent(JSON.stringify({ status: 'ok', ticketRef: result }));
    } else {
      output.setContent(JSON.stringify({ status: 'error', message: 'Unknown action' }));
    }
  } catch(err) {
    output.setContent(JSON.stringify({ status: 'error', message: err.message }));
  }

  return output;
}

// ── Write ticket to sheet + send emails ─────────────────────────────────────
function insertTicket(p) {
  var sheet = getOrCreateSheet();
  var ticketRef = p.ticketRef || generateRef();
  var timestamp = p.timestamp  || new Date().toISOString();

  // Build row in column order
  var row = [
    ticketRef,
    timestamp,
    p.email        || '',
    p.name         || '',
    p.season       || '',
    p.team         || '',
    p.purpose      || '',
    // Player move
    p.requestType  || '',
    p.playerName   || '',
    p.fromTeam     || '',
    p.toTeam       || '',
    p.removeFromTeam || '',
    // Scorecard
    p.matchLabel   || '',
    p.scorecardLink || '',
    // Scheduling
    p.weekend      || '',
    p.slotsToAvoid || '',
    p.notes        || '',  // umpire sched notes
    // Generic
    p.description  || '',
    // Extra notes (future use)
    '',
  ];

  sheet.appendRow(row);

  // Send emails
  sendAdminEmail(p, ticketRef);
  if (p.email) sendConfirmationEmail(p, ticketRef);

  return ticketRef;
}

// ── Admin notification email ─────────────────────────────────────────────────
function sendAdminEmail(p, ticketRef) {
  var subject = 'NACL ' + (p.season || '') + ', Ref #' + ticketRef + ', ' + (p.purpose || 'New Ticket') + ', ' + (p.team || '');

  var body = adminEmailHtml(p, ticketRef);

  var options = {
    htmlBody:  body,
    replyTo:   p.email || ADMIN_EMAIL,
    name:      'NACL Ticket System',
  };

  // Add category-specific recipients (as CC) when the Purpose matches.
  var extra = CATEGORY_EXTRA_EMAILS[p.purpose];
  if (extra && extra.length) options.cc = extra.join(',');

  GmailApp.sendEmail(ADMIN_EMAIL, subject, stripHtml(body), options);
}

// ── Submitter confirmation email ─────────────────────────────────────────────
function sendConfirmationEmail(p, ticketRef) {
  var subject = 'NACL ' + (p.season || '') + ', Ref #' + ticketRef + ', ' + (p.purpose || 'New Ticket') + ', ' + (p.team || '');

  var body = confirmationEmailHtml(p, ticketRef);

  GmailApp.sendEmail(p.email, subject, stripHtml(body), {
    htmlBody:  body,
    replyTo:   ADMIN_EMAIL,
    name:      'NACL Support',
  });
}

// ── Admin email HTML ─────────────────────────────────────────────────────────
function adminEmailHtml(p, ticketRef) {
  var detailRows = buildDetailRows(p);

  return '<!DOCTYPE html><html><body style="font-family:Arial,sans-serif;background:#f0f3f9;padding:20px;">' +
    '<div style="max-width:600px;margin:0 auto;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.1);">' +

    // Header
    '<div style="background:#012169;padding:20px 24px;display:flex;align-items:center;">' +
    '<div style="color:#fff;">' +
    '<div style="font-size:11px;letter-spacing:2px;text-transform:uppercase;color:#c8d1e8;margin-bottom:4px;">NACL Support</div>' +
    '<div style="font-size:20px;font-weight:bold;">New Ticket Received</div>' +
    '</div></div>' +

    // Ticket ref banner
    '<div style="background:#da291c;padding:10px 24px;color:#fff;font-size:13px;">' +
    '🎫 <strong>' + ticketRef + '</strong> &nbsp;·&nbsp; ' + (p.purpose || '') +
    '</div>' +

    // Body
    '<div style="padding:24px;">' +
    '<table style="width:100%;border-collapse:collapse;font-size:14px;">' +
    detailRows +
    '</table>' +
    '<div style="margin-top:20px;padding:14px;background:#f0f3f9;border-radius:6px;font-size:12px;color:#666;">' +
    'Reply directly to this email to respond to <strong>' + (p.name || 'the submitter') + '</strong> — ' +
    'your reply will go to <a href="mailto:' + (p.email || '') + '">' + (p.email || '') + '</a>' +
    '</div>' +
    '</div>' +

    // Footer
    '<div style="background:#f8f8f8;padding:14px 24px;font-size:11px;color:#aaa;border-top:1px solid #eee;">' +
    'Submitted via NACL Ticket System · ' + new Date().toUTCString() +
    '</div>' +
    '</div></body></html>';
}

// ── Confirmation email HTML ──────────────────────────────────────────────────
function confirmationEmailHtml(p, ticketRef) {
  return '<!DOCTYPE html><html><body style="font-family:Arial,sans-serif;background:#f0f3f9;padding:20px;">' +
    '<div style="max-width:600px;margin:0 auto;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.1);">' +

    // Header
    '<div style="background:#012169;padding:20px 24px;">' +
    '<div style="font-size:11px;letter-spacing:2px;text-transform:uppercase;color:#c8d1e8;margin-bottom:4px;">NACL Support</div>' +
    '<div style="font-size:20px;font-weight:bold;color:#fff;">We\'ve received your ticket</div>' +
    '</div>' +

    // Ticket ref banner
    '<div style="background:#22a855;padding:10px 24px;color:#fff;font-size:13px;">' +
    '✅ Your Ticket ID: <strong>' + ticketRef + '</strong>' +
    '</div>' +

    // Body
    '<div style="padding:24px;">' +
    '<p style="font-size:14px;color:#333;margin:0 0 16px;">Hi <strong>' + (p.name || 'there') + '</strong>,</p>' +
    '<p style="font-size:14px;color:#333;margin:0 0 16px;">' +
    'Your ticket has been received and the NACL committee will review it shortly. ' +
    'Please keep your Ticket ID for reference — use it in any follow-up communication.' +
    '</p>' +

    // Ticket details box — full details same as admin email
    '<div style="background:#f0f3f9;border-radius:8px;padding:16px;margin:16px 0;">' +
    '<div style="font-size:11px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;color:#666;margin-bottom:10px;">Ticket Details</div>' +
    '<table style="width:100%;font-size:13px;border-collapse:collapse;">' +
    emailRow('Ticket ID', '<strong style="color:#012169;">' + ticketRef + '</strong>') +
    buildDetailRows(p) +
    '</table></div>' +

    '<p style="font-size:13px;color:#666;margin:16px 0 0;">' +
    'If you have additional information to add, simply reply to this email and reference your Ticket ID <strong>' + ticketRef + '</strong>.' +
    '</p>' +
    '</div>' +

    // Footer
    '<div style="background:#f8f8f8;padding:14px 24px;font-size:11px;color:#aaa;border-top:1px solid #eee;">' +
    'This is an automated message from NACL Ticket System. ' +
    'Reply to this email to contact the NACL committee.' +
    '</div>' +
    '</div></body></html>';
}

// ── Build detail rows for admin email ───────────────────────────────────────
function buildDetailRows(p) {
  var rows = '';

  // Always shown
  rows += emailRow('Submitted By', (p.name || '—') + ' &lt;' + (p.email || '—') + '&gt;');
  rows += emailRow('Season',  p.season || '—');
  rows += emailRow('Team',    p.team   || '—');
  rows += emailRow('Purpose', '<strong>' + (p.purpose || '—') + '</strong>');

  // Purpose-specific fields
  if (p.requestType) {
    rows += sectionDivider();
    rows += emailRow('Request Type', p.requestType);
  }
  if (p.playerName)     rows += emailRow('Player Name',       p.playerName);
  if (p.fromTeam)       rows += emailRow('Previous Team',     p.fromTeam);
  if (p.toTeam)         rows += emailRow('Move To Team',      p.toTeam);
  if (p.removeFromTeam) rows += emailRow('Remove From Team',  p.removeFromTeam);

  if (p.matchLabel) {
    rows += sectionDivider();
    rows += emailRow('Match', p.matchLabel);
  }
  if (p.scorecardLink)  rows += emailRow('Scorecard Link',
    '<a href="' + p.scorecardLink + '">' + p.scorecardLink + '</a>');

  if (p.weekend) {
    rows += sectionDivider();
    rows += emailRow('Weekend', p.weekend);
  }
  if (p.slotsToAvoid)   rows += emailRow('Slots to Avoid',   p.slotsToAvoid);
  if (p.notes)          rows += emailRow('Umpire Notes',      p.notes);

  if (p.description) {
    rows += sectionDivider();
    rows += emailRow('Description',
      '<div style="background:#f8f8f8;padding:10px;border-radius:4px;white-space:pre-wrap;">' +
      escHtml(p.description) + '</div>');
  }

  return rows;
}

// ── Email helpers ────────────────────────────────────────────────────────────
function emailRow(label, value) {
  return '<tr>' +
    '<td style="padding:7px 12px 7px 0;color:#888;font-size:12px;font-weight:bold;' +
    'text-transform:uppercase;letter-spacing:.5px;white-space:nowrap;vertical-align:top;width:140px;">' +
    label + '</td>' +
    '<td style="padding:7px 0;font-size:13px;color:#222;border-bottom:1px solid #f0f0f0;">' +
    value + '</td>' +
    '</tr>';
}

function sectionDivider() {
  return '<tr><td colspan="2" style="padding:6px 0;">' +
    '<div style="border-top:1px solid #eee;margin:4px 0;"></div>' +
    '</td></tr>';
}

function escHtml(s) {
  return (s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function stripHtml(html) {
  return html.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
}

// ── Sheet helpers ────────────────────────────────────────────────────────────
function getOrCreateSheet() {
  var ss    = SpreadsheetApp.openById(SHEET_ID);
  var sheet = ss.getSheetByName(SHEET_NAME);

  if (!sheet) {
    // Create the tab and write header row
    sheet = ss.insertSheet(SHEET_NAME);
    sheet.appendRow(COLUMNS);

    // Style the header row
    var hdr = sheet.getRange(1, 1, 1, COLUMNS.length);
    hdr.setBackground('#012169');
    hdr.setFontColor('#ffffff');
    hdr.setFontWeight('bold');
    hdr.setFontSize(11);
    sheet.setFrozenRows(1);

    // Set reasonable column widths
    sheet.setColumnWidth(1, 160);  // Ticket ID
    sheet.setColumnWidth(2, 180);  // Timestamp
    sheet.setColumnWidth(3, 200);  // Email
    sheet.setColumnWidth(18, 300); // Description
  }

  return sheet;
}

function generateRef() {
  return 'NACL-' + Date.now().toString(36).toUpperCase();
}
