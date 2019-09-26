import React, { Component } from 'react';

import _ from 'lodash';
import ResultFrame from './result-frame';
// import PropTypes from 'prop-types';

// a div containing a page's results in order
class OdinsonResults extends Component {
  constructor(props) {
    super(props);

    this.state = {
      expanded: null,
    };
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
    const { scoreDocs, expanded } = this.props;
    const groupedResults = _(scoreDocs)
      .groupBy(sd => sd.documentId)
      .mapValues(group => _(group)
        .orderBy(elem => elem.sentenceIndex)
        .value()).value();
    console.log(groupedResults);
    const resultElements = Object.keys(groupedResults).map((parentDocId) => {
      const sds = groupedResults[parentDocId];
      // console.log(scoreDocs);
      const parentDocDisplayStr = `Parent Doc: ${parentDocId}`;
      const scoreDocsGroup = sds.map((scoreDoc) => {
        return (
          <ResultFrame
            odinsonDocId={scoreDoc.odinsonDoc}
            odinsonJson={scoreDoc}
            key={`result-frame-${scoreDoc.odinsonDoc}`}
            expanded={expanded}
          />
        );
      });
      return (
        <div className="parentDoc" key={`container-${parentDocId}`}>
          <h2>{parentDocDisplayStr}</h2>
          {scoreDocsGroup}
        </div>
      );
    });
    return (
      <div className="scoreDocs">
        {resultElements}
      </div>
    );
  }
}

export default OdinsonResults;
