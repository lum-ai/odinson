const config = {};

config.port = 7777;
config.odinsonApiBaseUrl = (process.env.ODINSON_API_BASE_URL || "http://localhost:9000/api");
config.odinsonQueryParam = "odinsonQuery";
config.parentQueryParam  = "parentQuery";

// TAG settings for Odinson
config.tag = {};
config.tag.showTopArgLabels   = false;
config.tag.bottomLinkCategory = "universal-enhanced";
config.tag.linkSlotInterval   = 15;
config.tag.compactRows        = true;

module.exports = config;
