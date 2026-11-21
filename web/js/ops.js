// Changes made on the web, as small operations the phone applies to its own data.
// The phone stays the one source of truth: the web sends an op, shows it straight away,
// and the phone folds it in next time it syncs. The Android app applies the same four kinds.
//
//   { id, at, op: 'upsert', vid, coll, item }   add or replace an item (matched by item.id)
//   { id, at, op: 'delete', vid, coll, itemId } remove an item
//   { id, at, op: 'patch',  vid, patch }        change fields of a vehicle (vid) or, with no vid, of the profile
//   { id, at, op: 'odo',    vid, km }           set the odometer (trip meters are left alone)
//   { id, at, op: 'batch',  ops: [...] }        several of the above that belong together (one form), applied in order
//
// coll is one of the per-vehicle lists below. Unknown kinds or collections are ignored, so an
// older phone never breaks on a newer web app.
export const COLLS = ['fuel', 'records', 'service', 'checklist', 'trips'];
export const VEHICLE_FIELDS = ['nickname', 'plate', 'colour', 'model', 'fuelPrice'];
export const PROFILE_FIELDS = ['name', 'city', 'country'];

const clean = (o, keys) => Object.fromEntries(Object.entries(o || {}).filter(([k]) => keys.includes(k)));

/** Applies one op to plain state (the JSON shape both apps save). Returns true if anything changed. */
export function applyOp(state, op) {
  if (!state || !op || typeof op !== 'object') return false;
  if (op.op === 'batch') return Array.isArray(op.ops) && op.ops.slice(0, 50).map((o) => o?.op !== 'batch' && applyOp(state, o)).some(Boolean);
  const v = op.vid ? (state.vehicles || []).find((x) => x.id === op.vid) : null;
  if (op.op === 'upsert' || op.op === 'delete') {
    if (!v || !COLLS.includes(op.coll)) return false;
    const list = Array.isArray(v[op.coll]) ? v[op.coll] : (v[op.coll] = []);
    if (op.op === 'delete') { const i = list.findIndex((x) => x.id === op.itemId); if (i < 0) return false; list.splice(i, 1); return true; }
    if (!op.item || typeof op.item.id !== 'string') return false;
    const i = list.findIndex((x) => x.id === op.item.id);
    if (i >= 0) list[i] = { ...list[i], ...op.item }; else list.push(op.item);
    return true;
  }
  if (op.op === 'patch') {
    if (op.vid) { if (!v) return false; Object.assign(v, clean(op.patch, VEHICLE_FIELDS)); return true; }
    state.profile = { ...(state.profile || {}), ...clean(op.patch, PROFILE_FIELDS) };
    return true;
  }
  if (op.op === 'odo') {
    if (!v || !(op.km >= 0)) return false;
    v.odo = Math.round(op.km * 10) / 10;
    return true;
  }
  return false;
}
