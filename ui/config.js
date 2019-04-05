const config = {};

config.port = 7777;
config.odinsonApiBaseUrl = (process.env.ODINSON_API_BASE_URL || "http://localhost:9000/api");
config.queryParams              = {};
config.queryParams.odinsonQuery = "odinsonQuery";
config.queryParams.parentQuery  = "parentQuery";
config.queryParams.label        = "label";
config.queryParams.commit       = "commit";
config.queryParams.prevDoc      = "prevDoc";
config.queryParams.prevScore    = "prevScore";
// to expand sentence to full info
config.sentParams               = {};
config.sentParams.sentId        = "sentId";
// TAG settings for Odinson
config.tag = {};
config.tag.showTopArgLabels   = false;
config.tag.bottomLinkCategory = "universal-enhanced";
config.tag.linkSlotInterval   = 15;
config.tag.compactRows        = true;

module.exports = config;
