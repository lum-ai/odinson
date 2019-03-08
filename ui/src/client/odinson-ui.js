import React, { Component } from 'react';
//import { Appdiv } from './UIElements';
import {
  AnchorButton,
  Card,
  Classes,
  Elevation,
  InputGroup
} from "@blueprintjs/core";
import './app.css';
import "text-annotation-graphs/dist/tag/css/tag.css"
import OdinsonTAG from './odinson-tag';

import Terminal from 'terminal-in-react';
import QueryDetails from './query-details';

import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { css } from 'glamor';

import axios from 'axios';
import _ from 'lodash';

const config = require('../../config');


//import ReactImage from './react.png';

export default class OdinsonUI extends Component {
  constructor(props) {
    super(props);

    this.state = {
      errorMsg: null,
      odinsonQuery: null,
      parentQuery: null,
      results: null
    };

    this.runQuery           = this.runQuery.bind(this);
    this.resetResults       = this.resetResults.bind(this);
    this.updateOdinsonQuery = this.updateOdinsonQuery.bind(this);
    this.updateParentQuery  = this.updateParentQuery.bind(this);
  }

  // Empty results when submitting query
  resetResults() {
    this.setState({
      errorMsg : null,
      results  : null
    });
  }

  componentDidMount() {
    // TODO: retrieve/build autocomplete
  }

  runQuery(commit=false, label=null) {
    if (this.state.odinsonQuery) {
      console.log(`runQuery(${commit}, ${label})`);
      this.resetResults();
      const data = {};
      data[config.queryParams.odinsonQuery] = this.state.odinsonQuery;
      data[config.queryParams.parentQuery]  = this.state.parentQuery;
      data[config.queryParams.commit]       = commit;
      data[config.queryParams.label]        = label;
      axios.get('api/search', {
        params: data,
      }).then(res => {
        const response = res.data;
        if (response.hasOwnProperty('error')) {
          this.setState({errorMsg: response.error});
        } else {
          this.setState({
            results     : response
          });
        }
      });
    } else {
      toast.error(`No query provided!`, {
        position: toast.POSITION.TOP_RIGHT,
        autoClose: 2000,
        toastId: 42,
        className: css({
          background: 'rgb(220,20,60)'
        })
      });
    }
  }

  // callback for submission
  updateOdinsonQuery(event) {
    this.setState({odinsonQuery: event.target.value});
  }

  updateParentQuery(event) {
    this.setState({parentQuery: event.target.value});
  }

  renderSentenceJson(event) {
    console.log(event);
  }

  // creates input fields for search
  createSearchInterface() {
    return (
      <div className="searchParams">
        <Terminal
          watchConsoleLogging={false}
          hideTopBar={true}
          allowTabs={false}
          style={{
            height: "20vh",
            fontWeight: "bold",
            fontSize: "1em"
          }}
          commands={{
            ":query": (args, print, runCommand) => {
              if (args.length == 1) {
                this.runQuery();
                print(`Running query...`);
              } else {
                print(`ERROR: unrecognized command`);
              }
            },
            ":commit": (args, print, runCommand) => {
              if (args.length == 1) {
                this.runQuery(true);
                print(`Running query and committing results...`);
              } else if (args.length == 2) {
                const label = args[1];
                console.log(`label: ${label}`);
                this.runQuery(true, label);
                print(`Running query and committing results as label '${label}'...`);
              } else {
                  print("ERROR: unrecognized command");
              }
            }
          }}
          descriptions={{
            ":query": "\n\tRuns the odinson and optional parent query",
            ":commit": ":commit <label>\n\tCommits the odinson results to the state using the provided label"
          }}
        />
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            width: "1000px"
          }}
          >
          <InputGroup
            type="text"
            name="odinsonQuery"
            className="queryString"
            placeholder="Odinson Query"
            onChange={this.updateOdinsonQuery}
            />
          <InputGroup
            type="text"
            name="parentQuery"
            className="queryString"
            placeholder="Parent Query"
            onChange={this.updateParentQuery}
            />
        </div>
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
    const groupedResults = _(this.state.results.scoreDocs)
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
          <ToastContainer/>
          {this.createSearchInterface()}
          <hr/>
          <div className="errorMsg">
            {this.state.errorMsg}
          </div>
        </div>
      );
    } else if (this.state.results) {
      return (
        <div
          >
          <ToastContainer/>
          {this.createSearchInterface()}
          <QueryDetails
            duration={this.state.results.duration}
            totalHits={this.state.results.totalHits}
            odinsonQuery={this.state.results.odinsonQuery}
            parentQuery={this.state.results.parentQuery}
          />
          <hr></hr>
          {this.createResultsDiv()}
        </div>
      )
    } else {
      return (
        <div
          >
          <ToastContainer/>
          {this.createSearchInterface()}
        </div>
      )
    }
  }
}
