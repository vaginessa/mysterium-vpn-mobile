/*
 * Copyright (C) 2018 The 'MysteriumNetwork/mysterion' Authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import { observer } from 'mobx-react/native'
import React, { ReactNode } from 'react'
import { View } from 'react-native'
import IFeedbackReporter from '../bug-reporter/feedback-reporter'
import TequilApiDriver from '../libraries/tequil-api/tequil-api-driver'
import AppLoader from './app-loader'
import styles from './app-styles'
import ErrorDropdown from './components/error-dropdown'
import MessageDisplayDelegate from './messages/message-display-delegate'
import Favorites from './proposals/favorites'
import ProposalList from './proposals/proposal-list'
import FeedbackScreen from './screens/feedback-screen'
import LoadingScreen from './screens/loading-screen'
import VpnScreen from './screens/vpn-screen'
import ConnectionStore from './stores/connection-store'
import ScreenStore from './stores/screen-store'
import VpnAppState from './vpn-app-state'

type AppProps = {
  tequilAPIDriver: TequilApiDriver,
  connectionStore: ConnectionStore,
  vpnAppState: VpnAppState,
  screenStore: ScreenStore,
  messageDisplayDelegate: MessageDisplayDelegate,
  proposalList: ProposalList,
  favorites: Favorites,
  appLoader: AppLoader,
  feedbackReporter: IFeedbackReporter
}

@observer
export default class App extends React.Component<AppProps> {
  private readonly tequilAPIDriver: TequilApiDriver
  private readonly connectionStore: ConnectionStore
  private readonly messageDisplayDelegate: MessageDisplayDelegate
  private readonly vpnAppState: VpnAppState
  private readonly screenStore: ScreenStore
  private readonly proposalList: ProposalList
  private readonly favorites: Favorites
  private readonly appLoader: AppLoader
  private readonly feedbackReporter: IFeedbackReporter

  constructor (props: AppProps) {
    super(props)
    this.tequilAPIDriver = props.tequilAPIDriver
    this.connectionStore = props.connectionStore
    this.messageDisplayDelegate = props.messageDisplayDelegate
    this.vpnAppState = props.vpnAppState
    this.screenStore = props.screenStore
    this.proposalList = props.proposalList
    this.favorites = props.favorites
    this.appLoader = props.appLoader
    this.feedbackReporter = props.feedbackReporter
  }

  public render (): ReactNode {
    return (
      <View style={styles.app}>
        {this.renderCurrentScreen()}
        <ErrorDropdown ref={(ref: ErrorDropdown) => this.messageDisplayDelegate.messageDisplay = ref}/>
      </View>
    )
  }

  public async componentDidMount () {
    try {
      await this.appLoader.load()
      this.screenStore.navigateToVpnScreen()
    } catch (err) {
      console.log('App loading failed', err)
    }
  }

  private renderCurrentScreen (): ReactNode {
    if (this.screenStore.inLoadingScreen) {
      return (
        <LoadingScreen/>
      )
    }

    if (this.screenStore.inFeedbackScreen) {
      return (
        <FeedbackScreen
          feedbackReporter={this.feedbackReporter}
          navigateBack={() => this.screenStore.navigateToVpnScreen()}
        />
      )
    }

    return (
      <VpnScreen
        tequilAPIDriver={this.tequilAPIDriver}
        connectionStore={this.connectionStore}
        vpnAppState={this.vpnAppState}
        screenStore={this.screenStore}
        proposalList={this.proposalList}
        favorites={this.favorites}
        messageDisplay={this.messageDisplayDelegate}
      />
    )
  }

}
