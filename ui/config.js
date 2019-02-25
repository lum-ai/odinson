const config = {};

config.port = 7777;
config.odinsonApiBaseUrl = (process.env.ODINSON_API_BASE_URL || "http://localhost:9000/api");
config.odinsonQueryParam = "odinsonQuery";
module.exports = config;
