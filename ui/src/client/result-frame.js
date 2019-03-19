import React, { Component } from 'react';
import ReactHtmlParser, { processNodes, 
  convertNodeToElement, 
  htmlparser2 
} from 'react-html-parser';
import TAG from 'text-annotation-graphs';
import {
  ButtonGroup,
  Button,
  Classes,
  Tooltip
} from '@blueprintjs/core';
import PropTypes from 'prop-types';

const config = require('../../config');

export default class ResultFrame extends Component {
  constructor(props) {
    super(props);
    this.state = {
      expanded: props.expanded
    };
  }

  componentWillReceiveProps(nextProps) {
    this.setState({ expanded: nextProps.expanded });
  }

  render() {
    if(this.state.expanded) {
      return (
        <div className='sentenceResults'>
          <div className='row'>
            <Tooltip content='See less detail'>
              <Button
                icon='collapse-all'
                minimal={true}
                large={true}
                onClick={() => this.setState({expanded: false})}
              />
            </Tooltip>
            <h3>Sentence ID: {this.props.odinsonDocId}</h3>
          </div>
          <OdinsonTAG
            odinsonJson={this.props.odinsonJson}
          />
        </div>
      )
    } else {
      return (
        <div className='sentenceResults'>
          <div className='row'>
            <Tooltip content='See more detail'>
              <Button
                icon='expand-all'
                minimal={true}
                large={true}
                onClick={() => this.setState({expanded: true})}
              />
            </Tooltip>
            <h3>Sentence ID: {this.props.odinsonDocId}</h3>
          </div>
          <OdinsonHighlight
            odinsonJson={this.props.odinsonJson}
          />
        </div>
      )
    }
  }
}

ResultFrame.propTypes = {
  expanded: PropTypes.bool.isRequired,
  odinsonDocId: PropTypes.number.isRequired,
  odinsonJson: PropTypes.object.isRequired
}

class OdinsonHighlight extends Component {
  constructor(props) {
    super(props);
    this.state = {}

    this.makeHighlights = this.makeHighlights.bind(this);
  }

  componentDidMount() {
    this.setState({
      displayText: this.makeHighlights()
    })
  }

  makeHighlights() {
    var base = this.props.odinsonJson.sentence.words.slice();
    var matches = this.props.odinsonJson.matches;
    matches.forEach(function (m) {
      const start = m.span.start;
      const end = m.span.end;
      base[start] = '<mark>' + base[start];
      base[end - 1] = base[end - 1] + '</mark>';
    })
    const displayText = base.join(' ');
    return <div>{ ReactHtmlParser(displayText) }</div>;
  }

  render() {
    return (
      <div className='highlights'>
        {this.state.displayText}
      </div>
    )
  }
}

OdinsonHighlight.propTypes = {
  odinsonJson: PropTypes.object.isRequired
}

class OdinsonTAG extends Component {
  constructor(props) {
    super(props);
    this.tagRef      = React.createRef();
    this.tagInstance = null;
  }

  componentDidMount() {
    const odinsonTAG = TAG.tag({
      container: this.tagRef.current,
      data: this.props.odinsonJson,
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

    return (
      // TODO: add div and dropdown button allowing one to reconfigure TAG elements
      <div ref={this.tagRef} className='scoreDoc'></div>
    )
  }
}

OdinsonTAG.propTypes = {
  odinsonJson: PropTypes.object.isRequired
}
