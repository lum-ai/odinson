import React, { Component } from 'react';

import OdinsonRoutes from './router';

//const config = require('../../config');

export default class App extends Component {
  constructor(props) {
    super(props);
  }

  render() {
    return <OdinsonRoutes />
  }
}
