import React, { Component } from 'react';

import { HTMLTable } from "@blueprintjs/core";

//const config = require('../../config');

// display query execution details
export default class QueryDetails extends Component {
  constructor(props) {
    super(props);
  }

  render() {
    const formattedDuration = `${Number(this.props.duration).toFixed(4)} seconds`;
    const formattedHits = this.props.totalHits.toLocaleString();
    return (
      <div className="queryDetails">
        <HTMLTable condensed={true} striped={true}>
          <tbody>
            <tr>
              <td><strong>Odinson Query</strong></td>
              <td className="queryString">{this.props.odinsonQuery}</td>
            </tr>
            <tr>
              <td><strong>Parent Query</strong></td>
              <td className="queryString">{this.props.parentQuery}</td>
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
    )
  }
}
