const axios = require('axios');

const express = require('express');
//const os = require('os');

const cors = require('cors');

const app = express();

const config = require('../../config');

app.use(cors());
app.use(express.static('dist'));

app.get('/api/sentence', (req, res) => {
  const data = {};
  data[config.sentParams.sentId] = req.query[config.sentParams.sentId];
  console.log(data);
  axios.get(`${config.odinsonApiBaseUrl}/sentence`, {
    headers: {'Access-Control-Allow-Origin': '*'},
    params: data,
    responseType: 'json'
  }).then(results => {
    res.json(results.data)
  }).catch(error => {
    res.json(error.response.data)
  });
});

app.get('/api/search', (req, res) => {
  const data = {};
  // handle odinson query
  data[config.queryParams.odinsonQuery] = req.query[config.queryParams.odinsonQuery];
  // handle parent query
  const pq = req.query[config.queryParams.parentQuery];
  if (pq) {
    data[config.queryParams.parentQuery]   = pq;
    console.log(`parentQuery: ${pq}`);
  }
  // handle page
  const prevDoc = req.query[config.queryParams.prevDoc];
  if (prevDoc) {
    data[config.queryParams.prevDoc] = prevDoc;
    console.log(`prevDoc: ${prevDoc}`);
  }
  const prevScore = req.query[config.queryParams.prevScore];
  if (prevScore) {
    data[config.queryParams.prevScore] = prevScore;
    console.log(`prevScore: ${prevScore}`);
  }
  // handle commit
  const commit = req.query[config.queryParams.commit];
  if (commit === true || commit === "true") {
    data[config.queryParams.commit]   = true;
    console.log(`commit: ${commit}`);
  }
  // handle label
  const label = req.query[config.queryParams.label];
  if (label) {
    data[config.queryParams.label]   = label;
    console.log(`label: ${label}`);
  }
  console.log(data);
  axios.get(`${config.odinsonApiBaseUrl}/search`, {
    headers: {'Access-Control-Allow-Origin': '*'},
    params: data,
    responseType: 'json'
  }).then(results => {
    //console.log(results.data);
    res.json(results.data)
  }).catch(error => {
    //console.log(error.response)
    res.json(error.response.data)
  });
});

app.get('/api/rich-search', (req, res) => {
  const data = {};
  // handle odinson query
  data[config.queryParams.odinsonQuery] = req.query[config.queryParams.odinsonQuery];
  // handle parent query
  const pq = req.query[config.queryParams.parentQuery];
  if (pq) {
    data[config.queryParams.parentQuery]   = pq;
    console.log(`parentQuery: ${pq}`);
  }
  // handle page
  const prevDoc = req.query[config.queryParams.prevDoc];
  if (prevDoc) {
    data[config.queryParams.prevDoc] = prevDoc;
    console.log(`prevDoc: ${prevDoc}`);
  }
  const prevScore = req.query[config.queryParams.prevScore];
  if (prevScore) {
    data[config.queryParams.prevScore] = prevScore;
    console.log(`prevScore: ${prevScore}`);
  }
  // handle commit
  const commit = req.query[config.queryParams.commit];
  if (commit === true || commit === "true") {
    data[config.queryParams.commit]   = true;
    console.log(`commit: ${commit}`);
  }
  // handle label
  const label = req.query[config.queryParams.label];
  if (label) {
    data[config.queryParams.label]   = label;
    console.log(`label: ${label}`);
  }
  console.log(data);
  axios.get(`${config.odinsonApiBaseUrl}/rich-search`, {
    headers: {'Access-Control-Allow-Origin': '*'},
    params: data,
    responseType: 'json'
  }).then(results => {
    //console.log(results.data);
    res.json(results.data)
  }).catch(error => {
    //console.log(error.response)
    res.json(error.response.data)
  });
});

//app.get('/api/getUsername', (req, res) => res.send({ username: os.userInfo().username }));
// app.get('/api/search', (req, res) =>
//   const resp = axios.get('http://pokeapi.co/api/v2/search')
//     .then((response) => {
//       return response;
//     })
//     .catch((error) => {
//       console.log(error);
//     });
//   }
//   res.send({ username: os.userInfo().username })
// );

app.listen(config.port, () => console.log(`Listening on port ${config.port}!`));
