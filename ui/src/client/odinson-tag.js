import React, { Component } from 'react';
import TAG from "text-annotation-graphs";

const config = require('../../config');

// Odinson TAG
export default class OdinsonTAG extends Component {
  constructor(props) {
    super(props);
    this.tagRef      = React.createRef();
    this.tagInstance = null;

  }

  componentDidMount() {
    const odinsonTAG = TAG.tag({
      container: this.tagRef.current,
      data: this.props.odinsonJson,
      format: "odinson",
      // Overrides for default options
      options: {
        showTopArgLabels: config.tag.showTopArgLabels,
        bottomLinkCategory: config.tag.bottomLinkCategory,
        linkSlotInterval: config.tag.linkSlotInterval,
        compactRows: config.tag.compactRows
      }
    });
    this.tagInstance = odinsonTAG;
  }

  shouldComponentUpdate() {
    return false;
  }

  render() {

    return (
      // TODO: add div and dropdown button allowing one to reconfigure TAG elements
      <div className="sentenceResults">
        <h3>Sentence ID: {this.props.odinsonDocId}</h3>
        <div ref={this.tagRef} className="scoreDoc"></div>
      </div>
    )
  }
}
