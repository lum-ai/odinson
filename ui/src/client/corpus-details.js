import React from 'react';

import { HTMLTable } from '@blueprintjs/core';

// const config = require('../../config');

// display query execution details
function CorpusDetails(props) {
  const { corpusName, totalDocs, numDistinctDeps } = props;
  const tds = totalDocs.toLocaleString();
  const ddc = numDistinctDeps.toLocaleString();
  return (
    <div className="corpusDetails">
      <HTMLTable 
        condensed={true} 
        striped={true}
      >
        <tbody>
          <tr>
            <td><strong>Corpus Details</strong></td>
          </tr>
          <tr>
            <td><strong>Corpus</strong></td>
            <td>{corpusName}</td>
          </tr>
          <tr>
            <td><strong>Total Docs</strong></td>
            <td>{tds}</td>
          </tr>
          <tr>
            <td><strong>Distinct Dependency Relations</strong></td>
            <td>{ddc}</td>
          </tr>
        </tbody>
      </HTMLTable>
    </div>
  );
}

export default CorpusDetails;
