import React, { Component } from 'react';
import PropTypes from 'prop-types';

import TAG from 'text-annotation-graphs';

const config = require('../../config');

// Odinson TAG
class OdinsonTAG extends Component {
  constructor(props) {
    super(props);
    this.tagRef = React.createRef();
    this.tagInstance = null;
  }

  componentDidMount() {
    const { odinsonJson } = this.props;
    const odinsonTAG = TAG.tag({
      container: this.tagRef.current,
      data: odinsonJson,
      format: 'odinson',
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
    const { odinsonDocId } = this.props;
    // const sentIdText = `Sentence ID: ${odinsonDocId}`;
    return (
      // TODO: add div and dropdown button allowing one to reconfigure TAG elements
      <div className="sentenceResults">
        {/* <h3>{sentIdText}</h3> */}
        <div
          ref={this.tagRef}
          className="scoreDoc"
        />
      </div>
    );
  }
}

OdinsonTAG.propTypes = {
  odinsonDocId: PropTypes.number,
  odinsonJson: PropTypes.object // FIXME: use PropTypes.shape({field1: ...});
};

OdinsonTAG.defaultProps = {
  odinsonDocId: null,
  odinsonJson: {}
};

export default OdinsonTAG;
