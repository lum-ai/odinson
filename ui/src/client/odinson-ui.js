import React, { Component } from 'react';
// import { Appdiv } from './UIElements';
import {
  // AnchorButton,
  Button,
  ButtonGroup,
  Tooltip,
  Position,
  // Card,
  // Classes,
  // Elevation,
  InputGroup,
  // Tooltip
} from '@blueprintjs/core';
import './app.css';
import 'text-annotation-graphs/dist/tag/css/tag.css';

import axios from 'axios';

import { ToastContainer, toast } from 'react-toastify';

import { css } from 'glamor';
import 'react-toastify/dist/ReactToastify.css';

// import PropTypes from 'prop-types';

import Terminal from 'terminal-in-react';
import QueryDetails from './query-details';
import OdinsonResults from './odinson-results';
import ExpandToggle from './expand-toggle';
// import CorpusDetails from './corpus-details';
import PageNavigation from './page-navigation';

const config = require('../../config');


class OdinsonUI extends Component {
  constructor(props) {
    super(props);

    this.state = {
      errorMsg: null,
      odinsonQuery: null,
      parentQuery: null,
      results: null,
      pageEnds: null,
      currentPage: null,
      totalPages: null,
      expanded: false,
      totalDocs: null,
      corpusName: null,
    };

    this.runQuery = this.runQuery.bind(this);
    this.resetResults = this.resetResults.bind(this);
    this.updateOdinsonQuery = this.updateOdinsonQuery.bind(this);
    this.updateParentQuery = this.updateParentQuery.bind(this);
    this.handleHeadClick = this.handleHeadClick.bind(this);
    this.handleLeftClick = this.handleLeftClick.bind(this);
    this.handleRightClick = this.handleRightClick.bind(this);
    // this.handleLastClick    = this.handleLastClick.bind(this);
    this.handleExpand = this.handleExpand.bind(this);
    this.handleCollapse = this.handleCollapse.bind(this);

    this.resultsRef = React.createRef();
  }

  componentDidMount() {
    // TODO: retrieve/build autocomplete
  }

  // Empty results when submitting query
  resetResults() {
    this.setState({
      errorMsg: null,
      results: null
    });
  }

  // Empty results when submitting query
  resetPages() {
    this.setState({
      pageEnds: null,
      currentPage: 1,
      totalPages: null
    });
  }

  runQuery(commit = false, label = null, newSearch = true, rich = false) {
    const {
      odinsonQuery,
      parentQuery,
      pageEnds
    } = this.state;
    if (odinsonQuery) {
      // console.log(`runQuery(${commit}, ${label}, ${newSearch})`);
      this.resetResults();
      if (newSearch) {
        this.resetPages();
      }
      const data = {};
      data[config.queryParams.odinsonQuery] = odinsonQuery;
      data[config.queryParams.parentQuery] = parentQuery;
      if (pageEnds && pageEnds.length > 0) {
        data[config.queryParams.prevDoc] = pageEnds.slice(-1).pop().odinsonDoc;
        data[config.queryParams.prevScore] = pageEnds.slice(-1).pop().score;
      }
      data[config.queryParams.commit] = commit;
      data[config.queryParams.label] = label;
      const searchRoute = rich ? 'api/rich-search' : 'api/search';
      axios.get(searchRoute, {
        params: data,
      }).then((res) => {
        const response = res.data;
        if (Object.prototype.hasOwnProperty.call(response, 'error')) {
          this.setState({ errorMsg: response.error });
        } else {
          this.setState({
            results: response
          });
          if (newSearch) {
            const tp = Math.ceil(response.totalHits / Math.max(response.scoreDocs.length, 1));
            console.log(`Total pages: ${tp}`);
            this.setState({
              totalPages: tp
            });
          }
        }
      });
    } else {
      toast.error('No query provided!', {
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
    this.setState({ odinsonQuery: event.target.value });
  }

  updateParentQuery(event) {
    this.setState({ parentQuery: event.target.value });
  }

  // creates input fields for search
  createSearchInterface() {
    const { expanded } = this.state;
    return (
      <div className="searchParams">
        <Terminal
          watchConsoleLogging={false}
          hideTopBar
          allowTabs={false}
          style={{
            height: '20vh',
            fontWeight: 'bold',
            fontSize: '1em'
          }}
          commands={{
            ':query': (args, print, runCommand) => {
              if (args.length === 1) {
                this.runQuery(false, null, true, expanded);
                print('Running query...');
              } else {
                print('ERROR: unrecognized command');
              }
            },
            ':commit': (args, print, runCommand) => {
              if (args.length === 1) {
                this.runQuery(true, null, true, expanded);
                print('Running query and committing results...');
              } else if (args.length === 2) {
                const label = args[1];
                console.log(`label: ${label}`);
                this.runQuery(true, label, true, expanded);
                print(`Running query and committing results as label '${label}'...`);
              } else {
                print('ERROR: unrecognized command');
              }
            }
          }}
          descriptions={{
            ':query': '\n\tRuns the odinson and optional parent query',
            ':commit': ':commit <label>\n\tCommits the odinson results to the state using the provided label'
          }}
        />
        <div className="searchFields">
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
          <ButtonGroup
            minimal
            large
          >
            <Tooltip
              content="Search"
              position={Position.RIGHT}
            >
              <Button
                className="odinsonSearchButton"
                icon="search-text"
                onClick={() => this.runQuery(false, null, true, expanded)}
              />
            </Tooltip>
          </ButtonGroup>
        </div>
      </div>
    );
  }


  // navigate to the first page (start query over again)
  handleHeadClick() {
    const { currentPage, expanded } = this.state;
    if (currentPage > 1) {
      this.runQuery(false, null, true, expanded);
      console.log('Requesting first page');
      // console.log('After: ' + this.state.pageEnds);
    }
  }

  // navigate to the previous page (query for docs before the first on the current page)
  handleLeftClick() {
    const { currentPage, pageEnds, expanded } = this.state;
    if (currentPage > 1) {
      // console.log('Before: ' + this.state.pageEnds);
      this.setState(
        {
          pageEnds: pageEnds.slice(0, -1),
          currentPage: currentPage - 1
        },
        () => {
          this.runQuery(false, null, false, expanded);
          console.log('Requesting previous page');
          // console.log('After: ' + this.state.pageEnds);
        }
      );
    }
  }

  // navigate to the next page (query for docs after the last on the current page)
  handleRightClick() {
    const { results, expanded, pageEnds, currentPage } = this.state;
    const nextprevDoc = [results.scoreDocs.slice(-1).pop()];
    if (pageEnds) {
      // console.log(`Before: ${pageEnds}`);
      this.setState(
        {
          pageEnds: pageEnds.concat(nextprevDoc),
          currentPage: currentPage + 1
        },
        () => {
          // console.log(`After: ${pageEnds}`);
          this.runQuery(false, null, false, expanded);
          console.log('Requesting next page');
        }
      );
    } else {
      this.setState(
        {
          pageEnds: nextprevDoc,
          currentPage: currentPage + 1
        },
        () => {
          // console.log('After: ' + this.state.pageEnds);
          this.runQuery(false, null, false, expanded);
          console.log('Requesting next page');
        }
      );
    }
  }

  // handle button press to go to the last page of results
  // handleLastClick () {
  // }

  handleExpand() {
    this.setState(() => ({ expanded: true }));
    this.runQuery(false, null, false, true);
  }

  handleCollapse() {
    this.setState({ expanded: false });
  }

  // As the name suggests, this is what controls the appearance/contents of the page.
  render() {
    const {
      errorMsg,
      results,
      currentPage,
      totalPages,
      pageEnds,
      expanded
    } = this.state;
    if (errorMsg) {
      return (
        <div>
          <ToastContainer />
          {this.createSearchInterface()}
          <hr />
          <div className="errorMsg">
            {errorMsg}
          </div>
        </div>
      );
    }

    if (results) {
      return (
        <div>
          <ToastContainer />
          {this.createSearchInterface()}
          <QueryDetails
            duration={results.duration}
            totalHits={results.totalHits}
            odinsonQuery={results.odinsonQuery}
            parentQuery={results.parentQuery}
          />
          {}
          <PageNavigation
            handleHeadClick={this.handleHeadClick}
            handleLeftClick={this.handleLeftClick}
            handleRightClick={this.handleRightClick}
            // handleLastClick={this.handleLastClick}
            currentPage={currentPage}
            totalPages={totalPages}
            pageEnds={pageEnds}
          />
          <ExpandToggle
            hidden={results.totalHits === 0}
            expanded={expanded}
            handleExpand={this.handleExpand}
            handleCollapse={this.handleCollapse}
          />
          <OdinsonResults
            scoreDocs={results.scoreDocs}
            expanded={expanded}
            ref={this.resultsRef}
          />
          <PageNavigation
            handleHeadClick={this.handleHeadClick}
            handleLeftClick={this.handleLeftClick}
            handleRightClick={this.handleRightClick}
            // handleLastClick={this.handleLastClick}
            currentPage={currentPage}
            totalPages={totalPages}
            pageEnds={pageEnds}
          />
        </div>
      );
    }

    return (
      <div>
        <ToastContainer />
        {this.createSearchInterface()}
      </div>
    );
  }
}

export default OdinsonUI;
