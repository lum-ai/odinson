import React, { Component } from 'react';
//import { Appdiv } from './UIElements';
import {
  AnchorButton,
  Button,
  Card,
  Classes,
  Elevation,
  InputGroup,
  Tooltip
} from '@blueprintjs/core';
import './app.css';
import 'text-annotation-graphs/dist/tag/css/tag.css'

import ResultFrame from './result-frame';
import Terminal from 'terminal-in-react';
import QueryDetails from './query-details';
import PageNavigation from './page-navigation';

import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { css } from 'glamor';

import axios from 'axios';
import _ from 'lodash';

import PropTypes from 'prop-types';

const config = require('../../config');


export default class OdinsonUI extends Component {
  constructor(props) {
    super(props);

    this.state = {
      errorMsg    : null,
      odinsonQuery: null,
      parentQuery : null,
      results     : null,
      pageEnds    : null,
      currentPage : null,
      totalPages  : null,
      expanded    : false
    };

    this.runQuery           = this.runQuery.bind(this);
    this.resetResults       = this.resetResults.bind(this);
    this.updateOdinsonQuery = this.updateOdinsonQuery.bind(this);
    this.updateParentQuery  = this.updateParentQuery.bind(this);
    this.handleHeadClick    = this.handleHeadClick.bind(this);
    this.handleLeftClick    = this.handleLeftClick.bind(this);
    this.handleRightClick   = this.handleRightClick.bind(this);
    // this.handleLastClick    = this.handleLastClick.bind(this);
    this.handleExpand       = this.handleExpand.bind(this);
    this.handleCollapse     = this.handleCollapse.bind(this);

    this.resultsRef = React.createRef();
  }

  // Empty results when submitting query
  resetResults() {
    this.setState({
      errorMsg: null,
      results : null
    });
  }

  // Empty results when submitting query
  resetPages() {
    this.setState({
      pageEnds   : null,
      currentPage: 1,
      totalPages : null
    });
  }

  componentDidMount() {
    // TODO: retrieve/build autocomplete
  }

  runQuery(commit=false, label=null, newSearch=true, rich=false) {
    if (this.state.odinsonQuery) {
      // console.log(`runQuery(${commit}, ${label}, ${newSearch})`);
      this.resetResults();
      if (newSearch) {
        this.resetPages();
      }
      const data = {};
      data[config.queryParams.odinsonQuery] = this.state.odinsonQuery;
      data[config.queryParams.parentQuery]  = this.state.parentQuery;
      if (this.state.pageEnds && this.state.pageEnds.length > 0) {
        data[config.queryParams.prevDoc]    = this.state.pageEnds.slice(-1).pop().odinsonDoc;
        data[config.queryParams.prevScore]  = this.state.pageEnds.slice(-1).pop().score;
      }
      data[config.queryParams.commit]       = commit;
      data[config.queryParams.label]        = label;
      const searchRoute = rich ? 'api/rich-search' : 'api/search';
      axios.get(searchRoute, {
        params: data,
      }).then(res => {
        const response = res.data;
        if (response.hasOwnProperty('error')) {
          this.setState({errorMsg: response.error});
        } else {
          this.setState({
            results: response
          });
          if (newSearch) {
            const tp = Math.ceil(response.totalHits / Math.max(response.scoreDocs.length, 1));
            console.log('Total pages: ' + tp);
            this.setState({
              totalPages: tp
            });
          }
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
      <div className='searchParams'>
        <Terminal
          watchConsoleLogging={false}
          hideTopBar={true}
          allowTabs={false}
          style={{
            height: '20vh',
            fontWeight: 'bold',
            fontSize: '1em'
          }}
          commands={{
            ':query': (args, print, runCommand) => {
              if (args.length == 1) {
                this.runQuery(false, null, true, this.state.expanded);
                print(`Running query...`);
              } else {
                print(`ERROR: unrecognized command`);
              }
            },
            ':commit': (args, print, runCommand) => {
              if (args.length == 1) {
                this.runQuery(true, null, true, this.state.expanded);
                print(`Running query and committing results...`);
              } else if (args.length == 2) {
                const label = args[1];
                console.log(`label: ${label}`);
                this.runQuery(true, label, true, this.state.expanded);
                print(`Running query and committing results as label '${label}'...`);
              } else {
                  print(`ERROR: unrecognized command`);
              }
            }
          }}
          descriptions={{
            ':query': '\n\tRuns the odinson and optional parent query',
            ':commit': ':commit <label>\n\tCommits the odinson results to the state using the provided label'
          }}
        />
        <div className='searchFields'>
          <InputGroup
            type='text'
            name='odinsonQuery'
            className='queryString'
            placeholder='Odinson Query'
            onChange={this.updateOdinsonQuery}
            />
          <InputGroup
            type='text'
            name='parentQuery'
            className='queryString'
            placeholder='Parent Query'
            onChange={this.updateParentQuery}
            />
        </div>
    </div>
    );
  }


  // navigate to the first page (start query over again)
  handleHeadClick() {
    if (this.state.currentPage > 1) {
      this.runQuery(false, null, true, this.state.expanded);
      console.log('Requesting first page');
      // console.log('After: ' + this.state.pageEnds);
    }
  }

  // navigate to the previous page (query for docs before the first on the current page)
  handleLeftClick() {
    if (this.state.currentPage > 1) {
      // console.log('Before: ' + this.state.pageEnds);
      this.setState(
        {
          pageEnds   : this.state.pageEnds.slice(0,-1),
          currentPage: this.state.currentPage - 1
        },
        function () {
          this.runQuery(false, null, false, this.state.expanded);
          console.log('Requesting previous page');
          // console.log('After: ' + this.state.pageEnds);
        }
      );
    }
  }

  // navigate to the next page (query for docs after the last on the current page)
  handleRightClick() {
    const nextprevDoc = [this.state.results.scoreDocs.slice(-1).pop()]
    if (this.state.pageEnds) {
      // console.log('Before: ' + this.state.pageEnds);
      this.setState(
        {
          pageEnds   : this.state.pageEnds.concat(nextprevDoc),
          currentPage: this.state.currentPage + 1
        },
        function () {
          // console.log('After: ' + this.state.pageEnds);
          this.runQuery(false, null, false, this.state.expanded);
          console.log('Requesting next page');
        }
      );
    } else {
      this.setState(
        {
          pageEnds   : nextprevDoc,
          currentPage: this.state.currentPage + 1
        },
        function () {
          // console.log('After: ' + this.state.pageEnds);
          this.runQuery(false, null, false, this.state.expanded);
          console.log('Requesting next page');
        }
      );
    }
  }

  // handle button press to go to the last page of results
  // handleLastClick () {
  // }

  handleExpand() {
    this.setState(function() {
      return ({'expanded': true});
    });
    this.runQuery(false, null, false, true);
  }

  handleCollapse() {
    this.setState({'expanded': false});
  }

  // As the name suggests, this is what controls the appearance/contents of the page.
  render() {

    if (this.state.errorMsg) {
      return (
        <div>
          <ToastContainer />
          {this.createSearchInterface()}
          <hr/>
          <div className='errorMsg'>
            {this.state.errorMsg}
          </div>
        </div>
      );
    }

    if (this.state.results) {
      return (
        <div>
          <ToastContainer />
          {this.createSearchInterface()}
          <QueryDetails
            duration={this.state.results.duration}
            totalHits={this.state.results.totalHits}
            odinsonQuery={this.state.results.odinsonQuery}
            parentQuery={this.state.results.parentQuery}
          />
          <PageNavigation
            handleHeadClick={this.handleHeadClick}
            handleLeftClick={this.handleLeftClick}
            handleRightClick={this.handleRightClick}
            // handleLastClick={this.handleLastClick}
            currentPage={this.state.currentPage}
            totalPages={this.state.totalPages}
            pageEnds={this.state.pageEnds}
          />
          <ExpandToggle
            hidden={this.state.results.totalHits === 0}
            expanded={this.state.expanded}
            handleExpand={this.handleExpand}
            handleCollapse={this.handleCollapse}
          />
          <Results
            scoreDocs={this.state.results.scoreDocs}
            expanded={this.state.expanded}
            ref={this.resultsRef}
          />
          <PageNavigation
            handleHeadClick={this.handleHeadClick}
            handleLeftClick={this.handleLeftClick}
            handleRightClick={this.handleRightClick}
            // handleLastClick={this.handleLastClick}
            currentPage={this.state.currentPage}
            totalPages={this.state.totalPages}
            pageEnds={this.state.pageEnds}
          />
        </div>
      );
    }

    return (
      <div>
        <ToastContainer/>
        {this.createSearchInterface()}
      </div>
    );
  }
}

// a div containing a page's results in order
class Results extends Component {
  constructor(props) {
    super(props);
  }

  componentWillReceiveProps(nextProps) {
    this.setState({ expanded: nextProps.expanded });
  }

  // process results
  render() {
    /*
    creates {
      parentdoc1: [scoreDoc1, scoreDoc2],
      parentdoc2: [scoreDoc1, scoreDoc2],
    }
    where values are sorted by their **order of appearance** in the document
    */
    const groupedResults = _(this.props.scoreDocs)
      .groupBy(sd => sd.documentId)
      .mapValues(group => _(group).orderBy(elem => elem.sentenceIndex).value())
      .value();
    console.log(groupedResults);
    const resultElements = Object.keys(groupedResults).map(parentDocId => {
      const scoreDocs = groupedResults[parentDocId];
      // console.log(scoreDocs);
      const scoreDocsGroup = scoreDocs.map(scoreDoc => {
        return <ResultFrame
          odinsonDocId={scoreDoc.odinsonDoc}
          odinsonJson={scoreDoc}
          key={`result-frame-${scoreDoc.odinsonDoc}`}
          expanded={this.props.expanded}
          />;
      });
      return (
        <div className='parentDoc' key={`container-${parentDocId}`}>
          <h2>Parent Doc: {parentDocId}</h2>
          {scoreDocsGroup}
        </div>
      );
    });
    return (
      <div className='scoreDocs'>
        {resultElements}
      </div>
    );
  }
}

// toggle button to expand all sentences to TAG representation or collapse to highlighted text
class ExpandToggle extends Component {
  constructor(props) {
    super(props);
  }

  render() {
    if(this.props.hidden) {
      return null
    }

    if(this.props.expanded) {
      return (
        <Tooltip content='Hide annotated view'>
          <Button
            icon='collapse-all'
            minimal={true}
            large={true}
            onClick={() => this.props.handleCollapse()}>
            Collapse all
          </Button>
        </Tooltip>
      );
    } else {
      return (
        <Tooltip content='Show annotated view'>
          <Button
            icon='expand-all'
            minimal={true}
            large={true}
            onClick={() => this.props.handleExpand()}>
            Expand all
          </Button>
        </Tooltip>
      );
    }
  }
}

ExpandToggle.propTypes = {
  expanded: PropTypes.bool.isRequired,
  handleExpand: PropTypes.func.isRequired,
  handleCollapse: PropTypes.func.isRequired
}

