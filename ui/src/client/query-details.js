import React from 'react';

import PropTypes from 'prop-types';

import { HTMLTable } from '@blueprintjs/core';

// const config = require('../../config');

// display query execution details
function QueryDetails(props) {
  const {
    duration,
    totalHits,
    odinsonQuery,
    parentQuery
  } = props;
  const formattedDuration = `${Number(duration).toFixed(4)} seconds`;
  const formattedHits = totalHits.toLocaleString();
  return (
    <div className="queryDetails">
      <HTMLTable
        condensed
        striped
      >
        <tbody>
          <tr>
            <td><strong>Odinson Query</strong></td>
            <td className="queryString">{odinsonQuery}</td>
          </tr>
          <tr>
            <td><strong>Parent Query</strong></td>
            <td className="queryString">{parentQuery}</td>
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
    </div>
  );
}

QueryDetails.propTypes = {
  duration: PropTypes.number.isRequired,
  totalHits: PropTypes.number.isRequired,
  odinsonQuery: PropTypes.string,
  parentQuery: PropTypes.string
};

QueryDetails.defaultProps = {
  odinsonQuery: null,
  parentQuery: null
};

export default QueryDetails;
