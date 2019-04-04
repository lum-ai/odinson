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

const axios = require('axios');

const config = require('../../config');

export default class ResultFrame extends Component {
  constructor(props) {
    super(props);
    this.state = {
      odinsonJson: props.odinsonJson,
      expanded: props.expanded
    };

    this.handleExpand       = this.handleExpand.bind(this);
  }

  componentWillReceiveProps(nextProps) {
    this.setState({ expanded: nextProps.expanded });
  }

  handleExpand() {
    if(! this.state.odinsonJson.hasOwnProperty('sentence')) {
      console.log("no sentence");
      const data = {};
      data[config.sentParams.sentId] = this.state.odinsonJson.odinsonDoc;
      console.log(data);
      axios.get('/api/sentence', {
        params: data,
      }).then(res => {
        const response = res.data;
        console.log(response);
        if (response.hasOwnProperty('error')) {
          this.setState({errorMsg: response.error});
        } else {
          var odinsonJson = this.state.odinsonJson;
          odinsonJson['sentence'] = response;
          this.setState({
            odinsonJson: odinsonJson,
            expanded: true
          });
        }
      }
    )}
    else{
      this.setState({expanded: true});
    }
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
                onClick={() => this.handleExpand()}
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
    if(this.props.odinsonJson.hasOwnProperty('sentence')) {
      var base = this.props.odinsonJson.sentence.words.slice();
    } else {
      var base = this.props.odinsonJson.words.slice();
    }
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
