import React, { Component } from 'react';
import PropTypes from 'prop-types';
import OdinsonTAG from './odinson-tag';
import OdinsonHighlight from './odinson-highlight';
import TAG from 'text-annotation-graphs';
import {
  ButtonGroup,
  Button,
  Classes,
  Tooltip,
  Position
} from '@blueprintjs/core';

const axios = require('axios');

const config = require('../../config');

class ResultFrame extends Component {
  constructor(props) {
    super(props);
    this.state = {
      odinsonJson: props.odinsonJson,
      expanded: props.expanded,
      errorMsg: null // FIXME: is this needed?
    };

    this.handleExpand = this.handleExpand.bind(this);
  }

  componentWillReceiveProps(nextProps) {
    this.setState({ expanded: nextProps.expanded });
  }

  handleExpand() {
    const { odinsonJson } = this.state;
    if (!Object.prototype.hasOwnProperty.call(odinsonJson, 'sentence')) {
      console.log('no sentence');
      const data = {};
      data[config.sentParams.sentId] = odinsonJson.odinsonDoc;
      console.log(data);
      axios.get('/api/sentence', {
        params: data,
      }).then((res) => {
        const response = res.data;
        console.log(response);
        if (Object.prototype.hasOwnProperty.call(response, 'error')) {
          this.setState({ errorMsg: response.error }); // FIXME: is this needed?
        } else {
          const oj = odinsonJson;
          oj.sentence = response;
          this.setState({
            odinsonJson: oj,
            expanded: true
          });
        }
      });
    } else {
      this.setState({ expanded: true });
    }
  }

  render() {
    const { expanded } = this.state;
    const {
      odinsonDocId,
      odinsonJson
    } = this.props;

    const sentIdDisplayText = `Sentence ID: ${odinsonDocId}`;
    if (expanded) {
      return (
        <div className="sentenceResults">
          <div className="row">
            <Tooltip content="See less detail">
              <Button
                icon="collapse-all"
                minimal
                large
                onClick={() => this.setState({ expanded: false })}
              />
            </Tooltip>
            <h3>{sentIdDisplayText}</h3>
          </div>
          <OdinsonTAG
            odinsonJson={odinsonJson}
          />
        </div>
      );
    }
    return (
      <div className="sentenceResults">
        <div className="row">
          <Tooltip
            content="See more detail"
            position={Position.LEFT}
          >
            <Button
              icon="expand-all"
              minimal
              large
              onClick={() => this.handleExpand()}
            />
          </Tooltip>
          <h3>{sentIdDisplayText}</h3>
        </div>
        <OdinsonHighlight
          odinsonJson={odinsonJson}
        />
      </div>
    );
  }
}

ResultFrame.propTypes = {
  expanded: PropTypes.bool.isRequired,
  odinsonDocId: PropTypes.number.isRequired,
  odinsonJson: PropTypes.object.isRequired // FIXME: use PropTypes.shape({field1: ...});
};

export default ResultFrame;
