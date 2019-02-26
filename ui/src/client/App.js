import React, { Component } from 'react';
import './app.css';
import "text-annotation-graphs/dist/tag/css/tag.css"
import axios from 'axios';
import OdinsonTAG from './OdinsonTAG.js';
import _ from 'lodash';

const config = require('../../config');


//import ReactImage from './react.png';

export default class App extends Component {
  constructor(props) {
    super(props);

    this.state = {
      errorMsg: null,
      odinsonQuery: null,
      oq: null, // for storing incremental query updates
      pq: null, // for storing incremental query updates
      parentQuery: null,
      duration: null,
      totalHits: null,
      scoreDocs: null
    };

    this.onSubmit           = this.onSubmit.bind(this);
    this.updateOdinsonQuery = this.updateOdinsonQuery.bind(this);
    //this.updateParentQuery  = this.updateParentQuery.bind(this);
  }


  componentDidMount() {
    // TODO: retrieve/build autocomplete
  }

  onSubmit(event) {
    const data = {};
    data[config.odinsonQueryParam] = this.state.oq;
    // FIXME: add parent query
    //data[config.parentQueryParm] = this.state.pq;
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
  updateOdinsonQuery(event) {
    this.setState({oq: event.target.value});
  }
  // TODO: add parent query
  // updateParentQuery(event) {
  //   this.setState({pq: event.target.value});
  // }

  renderSentenceJson(event) {
    console.log(event);
  }

  // creates input fields for search
  createSearchInterface() {
    return (
      <div>
        <input type="text" name="odinsonQuery" onChange={this.updateOdinsonQuery}></input>
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
    //console.log(groupedResults);
    const resultElements = Object.keys(groupedResults).map(parentDocId => {
      const scoreDocs = groupedResults[parentDocId];
      // console.log(scoreDocs);
      const scoreDocsGroup = scoreDocs.map(scoreDoc => {
        // FIXME: key has to be unique for tag to re-render when spans + doc order is the same.
        // Evaluate https://www.npmjs.com/package/weak-key as possible solution.
        // We need to either hash the json or use an ID that is guaranteed to be unique.
        return <OdinsonTAG
          odinsonDocId={scoreDoc.odinsonDoc}
          odinsonJson={scoreDoc}
          key={`odinson-tag-${scoreDoc.odinsonDoc}`}
          ></OdinsonTAG>;
      });
      // FIXME: key has to be unique for tag to re-render when spans + doc order is the same.
      // Evaluate https://www.npmjs.com/package/weak-key as possible solution.
      // We need to either hash the json or use an ID that is guaranteed to be unique.
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
