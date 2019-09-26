import React, { Component } from 'react';
import PropTypes from 'prop-types';
import ReactHtmlParser from 'react-html-parser';
// import ReactHtmlParser, {
//   processNodes,
//   convertNodeToElement,
//   htmlparser2
// } from 'react-html-parser';

class OdinsonHighlight extends Component {
  constructor(props) {
    super(props);
    this.state = {};
    this.makeHighlights = this.makeHighlights.bind(this);
  }

  componentDidMount() {
    this.setState({
      displayText: this.makeHighlights()
    });
  }

  makeHighlights() {
    const { odinsonJson } = this.props;
    let base = null;
    if (Object.prototype.hasOwnProperty.call(odinsonJson, 'sentence')) {
      base = odinsonJson.sentence.words.slice();
    } else {
      base = odinsonJson.words.slice();
    }
    const { matches } = odinsonJson;
    matches.forEach((m) => {
      const { start, end } = m.span;
      base[start] = `<mark>${base[start]}`;
      base[end - 1] = `${base[end - 1]}</mark>`;
    });
    const displayText = base.join(' ');
    return <div>{ ReactHtmlParser(displayText) }</div>;
  }

  render() {
    const { displayText } = this.state;
    return (
      <div className="highlights">
        {displayText}
      </div>
    );
  }
}

OdinsonHighlight.propTypes = {
  odinsonJson: PropTypes.object // FIXME: use PropTypes.shape({field1: ...});
};

OdinsonHighlight.defaultProps = {
  odinsonJson: {}
};

export default OdinsonHighlight;
