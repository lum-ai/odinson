import React, { Component } from 'react';
import './app.css';
import axios from 'axios';

import _ from 'lodash';

const config = require('../../config');


//import ReactImage from './react.png';

export default class App extends Component {
  constructor(props) {
    super(props);

    this.state = {
      errorMsg: null,
      odinsonQuery: null,
      parentQuery: null,
      duration: null,
      totalHits: null,
      scoreDocs: null
    };

    this.onSubmit    = this.onSubmit.bind(this);
    this.updateQuery = this.updateQuery.bind(this);
  }


  componentDidMount() {
    // TODO: retrieve/build autocomplete
  }

  onSubmit(event) {
    const data = {};
    data[config.odinsonQueryParam] = this.state.odinsonQuery;
    // FIXME: add parent query
    axios.get('api/search', {
      params: data,
    }).then(res => {
      const response = res.data;
      if (response.hasOwnProperty('error')) {
        this.setState({errorMsg: response.error});
      } else {
        this.setState({
          odinsonQuery: response.odinsonQuery,
          parentQuery : response.parentQuery,
          duration    : response.duration,
          totalHits   : response.totalHits,
          scoreDocs   : response.scoreDocs
        });
      }
    });
  }

  // callback for submission
  // FIXME: add pq
  updateQuery(event) {
    this.setState({odinsonQuery: event.target.value});
  }

  renderSentenceJson(event) {
    console.log(event);
  }

  // creates input fields for search
  createSearchInterface() {
    return (
      <div>
        <input type="text" name="odinsonQuery" onChange={this.updateQuery}></input>
         <button type="button" onClick={this.onSubmit} className="btn">Search</button>
      </div>
    );
  }

  // process query execution details
  // FIXME: convert this to a table
  createDetailsDiv() {
    return (
      <div>
        <p>Odinson Query: <span className="query">{this.state.odinsonQuery}</span></p>
        <p> Parent Query: <span className="query">{this.state.parentQuery}</span></p>
        <p>     Duration: <span className="duration">{Number(this.state.duration).toFixed(4)}</span> seconds</p>
        <p>         Hits: <span className="totalHits">{this.state.totalHits}</span></p>
      </div>
    );
  }

  // process results
  createResultsDiv() {
    /*
    creates {
      parentdoc1: [scoreDoc1, scoreDoc2],
      parentdoc2: [scoreDoc1, scoreDoc2],
    }
    where values are sorted by their **order of appearance** in the document
    */
    const groupedResults = _(this.state.scoreDocs)
    .groupBy(sd => sd.documentId)
    .mapValues(group => _(group).orderBy(elem => elem.sentenceIndex).value())
    .value();
    console.log(groupedResults);
    const resultElements = Object.keys(groupedResults).map(parentDocId => {
      const scoreDocs = groupedResults[parentDocId];
      console.log(scoreDocs);
      const scoreDocsGroup = scoreDocs.map(scoreDoc => {
        return (
          <div className="sentenceResults" key={scoreDoc.odinsonDoc}>
            <h3 onClick={() => this.renderSentenceJson(scoreDoc.odinsonDoc)}>Sentence ID: {scoreDoc.odinsonDoc}</h3>
            <div id={scoreDoc.odinsonDoc} className="scoreDoc" key={scoreDoc.odinsonDoc}>{JSON.stringify(scoreDoc, null, 2)}</div>
          </div>
        );
      });
      // FIXME: add TAG
      return (
        <div key={`container-${parentDocId}`}>
          <h2>Parent Doc: {parentDocId}</h2>
          {scoreDocsGroup}
        </div>
      );
    });
    return <div className="scoreDocs">{resultElements}</div>;
  }

  // As the name suggests, this is what controls the appearance/contents of the page.
  // TODO: Add react-tabs for [RESULTS, DETAILS] https://reactcommunity.org/react-tabs/
  render() {
    if (this.state.errorMsg) {
      return (
        <div>
          {this.createSearchInterface()}
          <div>
            {this.state.errorMsg}
          </div>
        </div>

      );
    } else if (this.state.scoreDocs) {
      return (
        <div>
          {this.createSearchInterface()}
          {this.createDetailsDiv()}
          <hr></hr>
          {this.createResultsDiv()}
        </div>
      )
    } else {
      return (
        <div>
          {this.createSearchInterface()}
        </div>
      )
    }
  }
}
