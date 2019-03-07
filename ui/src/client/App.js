import React, { Component } from 'react';
//import { Appdiv } from './UIElements';
import {
  AnchorButton,
  Card,
  Classes,
  Elevation,
  HTMLTable,
  InputGroup
} from "@blueprintjs/core";
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
    this.updateParentQuery  = this.updateParentQuery.bind(this);
  }


  componentDidMount() {
    // TODO: retrieve/build autocomplete
  }

  onSubmit(event) {
    const data = {};
    data[config.odinsonQueryParam] = this.state.oq;
    data[config.parentQueryParam]  = this.state.pq;
    axios.get('api/search', {
      params: data,
    }).then(res => {
      const response = res.data;
      if (response.hasOwnProperty('error')) {
        this.setState({errorMsg: response.error});
      } else {
        this.setState({
          errorMsg    : null,
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

  updateParentQuery(event) {
    this.setState({pq: event.target.value});
  }

  renderSentenceJson(event) {
    console.log(event);
  }

  // creates input fields for search
  createSearchInterface() {
    return (
      <div className="searchParams">
        <InputGroup
          type="text"
          name="odinsonQuery"
          placeholder="Odinson Query"
          onChange={this.updateOdinsonQuery}
          />
        <InputGroup
          type="text"
          name="parentQuery"
          placeholder="Parent Query"
          onChange={this.updateParentQuery}
          />
        <AnchorButton
        onClick={this.onSubmit}
        type="button"
        text="Search"
        className="btn"/>
    </div>
    );
  }
  // process query execution details
  createDetailsDiv() {
    const formattedDuration = `${Number(this.state.duration).toFixed(4)} seconds`;
    const formattedHits = this.state.totalHits.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");

    return (
      <HTMLTable>
        <tbody>
          <tr>
            <td><strong>Odinson Query</strong></td>
            <td className="queryString">{this.state.odinsonQuery}</td>
          </tr>
          <tr>
            <td><strong>Parent Query</strong></td>
            <td className="queryString">{this.state.parentQuery}</td>
          </tr>
          <tr>
            <td><strong>Duration</strong></td>
            <td>{formattedDuration}</td>
          </tr>
          <tr>
            <td><strong>Hits</strong></td>
            <td>{formattedHits}</td>
          </tr>
        </tbody>
      </HTMLTable>
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
        <div
          >
          {this.createSearchInterface()}
          <div>
            {this.state.errorMsg}
          </div>
        </div>
      );
    } else if (this.state.scoreDocs) {
      return (
        <div
          >
          {this.createSearchInterface()}
          {this.createDetailsDiv()}
          <hr></hr>
          {this.createResultsDiv()}
        </div>
      )
    } else {
      return (
        <div
          >
          {this.createSearchInterface()}
        </div>
      )
    }
  }
}
