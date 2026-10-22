const PROFILES = Object.freeze({
  NORMAL: Object.freeze({grade: 1, reticleSize: 1.1, hitBullseye: true}),
  NICE: Object.freeze({grade: 2, reticleSize: 0.85, hitBullseye: true}),
  GREAT: Object.freeze({grade: 3, reticleSize: 0.5, hitBullseye: true}),
  EXCELLENT: Object.freeze({grade: 4, reticleSize: 0.05, hitBullseye: true})
});
function profile(quality = 'GREAT') {
  if (typeof quality !== 'string' || !Object.prototype.hasOwnProperty.call(PROFILES, quality))
    throw Error('Invalid throw quality');
  return PROFILES[quality];
}
function pick(qualities) {
  const valid = Array.isArray(qualities) ? qualities.filter(q => Object.prototype.hasOwnProperty.call(PROFILES, q)) : [];
  const q = valid.length > 0 ? valid[Math.floor(Math.random() * valid.length)] : 'EXCELLENT';
  return {...PROFILES[q], quality: q};
}
module.exports = {PROFILES, profile, pick};
